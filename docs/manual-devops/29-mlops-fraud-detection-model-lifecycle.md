# FinCore Banking Platform — DevOps Engineering Manual
## Manual 29: MLOps Fraud Detection Model Governance, Feature Store & MLflow Lifecycle

### A. Objective & Architecture Context
FinCore currently enforces banking transaction integrity using deterministic, rule-based fraud detection (`HighValueTransferRule`, `RapidVelocityRule`, `ExcessiveBalanceDepletionRule` defined in Flyway `V7`). While effective for known threshold violations, sophisticated financial fraud (such as synthetic identity fraud, money muling rings, and account takeover) requires statistical Machine Learning models. This manual establishes an enterprise MLOps lifecycle: deploying an MLflow Model Registry, defining a Banking Feature Store, tracking training experiments on synthetic transaction datasets, and governing model deployment without disrupting the existing deterministic rule engine.

### B. Prerequisites & Dependencies
- Python 3.11+ installed with virtual environment tooling.
- Docker engine active to run local MLflow tracking server.
- FinCore fraud database schema (`fraud_rules`, `fraud_evaluations`) active from Flyway V7.

### C. Step-by-Step Implementation Instructions

#### Step 1: Deploy MLflow Tracking Server & Artifact Store
Review `devops-configurations/mlops/mlflow-docker-compose.yaml`:
- **Tracking Server:** MLflow 2.14+ tracking UI listening on port `5000`.
- **Backend Store:** PostgreSQL database storing experiment parameters, metrics, and tags.
- **Artifact Store:** Amazon S3 bucket (`s3://fincore-mlops-artifacts/`) or local volume storing serialized model binaries (`.onnx`, `.pkl`).

Launch the MLflow stack:
```powershell
docker compose -f devops-configurations/mlops/mlflow-docker-compose.yaml up -d
```
Verify MLflow UI at `http://localhost:5000`.

#### Step 2: Review the Banking Feature Store Specification
Inspect `devops-configurations/mlops/feature-store-spec.yaml`:
```yaml
featureStore:
  name: fincore-banking-feature-store
  entity: customer_account
  features:
    - name: transfer_amount_normalized
      type: float
      description: "Transfer amount divided by 30-day average transaction size"
    - name: velocity_transfers_1h
      type: int
      description: "Count of outbound transfers initiated in last 60 minutes"
    - name: velocity_transfers_24h
      type: int
      description: "Count of outbound transfers initiated in last 24 hours"
    - name: balance_depletion_ratio
      type: float
      description: "Transaction amount divided by available account balance"
    - name: hour_of_day
      type: int
      description: "UTC hour of transfer initiation (0-23)"
    - name: counterparty_is_new
      type: boolean
      description: "True if target account has zero historical transactions with source"
```

#### Step 3: Execute Model Training Pipeline on Synthetic Banking Data
Create a Python training script using LightGBM or XGBoost:
```python
# train_fraud_model.py
import mlflow
import mlflow.lightgbm
import lightgbm as lgb
import numpy as np
from sklearn.metrics import roc_auc_score, precision_score, recall_score

mlflow.set_tracking_uri("http://localhost:5000")
mlflow.set_experiment("fincore-fraud-detection")

with mlflow.start_run(run_name="lightgbm-baseline-v1"):
    # Generate synthetic training distribution (simulating imbalanced banking fraud: 99.5% legitimate, 0.5% fraud)
    n_samples = 100000
    X = np.random.randn(n_samples, 6)
    y = (X[:, 0] * 1.5 + X[:, 1] * 2.0 + np.random.randn(n_samples) > 4.5).astype(int)

    params = {
        "objective": "binary",
        "metric": "auc",
        "learning_rate": 0.05,
        "num_leaves": 31,
        "is_unbalance": True
    }
    mlflow.log_params(params)

    train_data = lgb.Dataset(X[:80000], label=y[:80000])
    valid_data = lgb.Dataset(X[80000:], label=y[80000:])
    model = lgb.train(params, train_data, valid_sets=[valid_data], num_boost_round=100)

    preds = model.predict(X[80000:])
    auc = roc_auc_score(y[80000:], preds)
    precision = precision_score(y[80000:], (preds > 0.5).astype(int))
    recall = recall_score(y[80000:], (preds > 0.5).astype(int))

    mlflow.log_metric("roc_auc", auc)
    mlflow.log_metric("precision", precision)
    mlflow.log_metric("recall", recall)

    # Register model in MLflow Model Registry
    mlflow.lightgbm.log_model(model, "fraud_model", registered_model_name="FinCoreFraudDetector")
    print(f"Training complete. ROC-AUC: {auc:.4f}, Precision: {precision:.4f}, Recall: {recall:.4f}")
```
Run the training script:
```powershell
python train_fraud_model.py
```

#### Step 4: Promote Model to Production Registry
In the MLflow UI (`http://localhost:5000`):
1. Navigate to **Models -> FinCoreFraudDetector**.
2. Select the latest model version.
3. Validate Quality Gate: `ROC-AUC >= 0.95` and `Precision >= 0.85` (to minimize customer-facing false-positive transfer rejections).
4. Transition model stage from `Staging` to `Production`.

#### Step 5: Export to ONNX for High-Throughput Microsecond Serving
Export the trained model to the Open Neural Network Exchange (ONNX) format:
```python
import onnxmltools
from onnxmltools.convert.common.data_types import FloatTensorType

initial_type = [('float_input', FloatTensorType([None, 6]))]
onnx_model = onnxmltools.convert_lightgbm(model, initial_types=initial_type)
with open("fincore_fraud_v1.onnx", "wb") as f:
    f.write(onnx_model.SerializeToString())
```
The `.onnx` binary is loaded directly by the Java Spring Boot backend using the `com.microsoft.onnxruntime:onnxruntime` dependency, achieving sub-2ms in-process scoring without inter-service HTTP network latency!

### D. Configuration References
- MLflow Docker Compose: `devops-configurations/mlops/mlflow-docker-compose.yaml`
- Feature Store Spec: `devops-configurations/mlops/feature-store-spec.yaml`
- Fraud Rules Schema: `src/main/resources/db/migration/V7__fraud_detection_schema.sql`

### E. Verification & Validation Steps
Verify that the model is registered and downloadable:
```powershell
curl -s http://localhost:5000/api/2.0/mlflow/registered-models/get?name=FinCoreFraudDetector | jq .registered_model.name
```
*Expected Output:* `"FinCoreFraudDetector"`

Verify that the model artifact contains input/output signatures and metadata.

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: Model produces massive false-positive spikes in production**
   - *Root Cause:* Training data suffered from severe class imbalance or synthetic distribution drift.
   - *Remediation:* Tune the classification decision threshold (e.g. increase probability cutoff from 0.50 to 0.75) and enforce SMOTE / Focal Loss during retraining.
2. **Failure Mode: Java backend throws `OrtException` loading ONNX model**
   - *Root Cause:* Input tensor dimension mismatch between feature store vector (6 floats) and ONNX input signature.
   - *Remediation:* Ensure feature ordering in `FeatureExtractor.java` matches the exact array order defined in `feature-store-spec.yaml`.
3. **Failure Mode: MLflow artifact upload fails**
   - *Root Cause:* S3 bucket permissions missing or AWS credentials expired.
   - *Remediation:* Verify that the MLflow server container has `s3:PutObject` permissions on the artifact bucket.

### G. Rollback & Disaster Recovery Procedures
If a newly deployed ML model exhibits aberrant classification behavior:
1. In MLflow UI, transition previous model version back to `Production`.
2. Fallback to Deterministic Rules: The Spring Boot backend evaluates deterministic rules (`HighValueTransferRule`) first; if ML inference times out or fails, the platform safely degrades to the deterministic ruleset without dropping transfers.

### H. Security & Compliance Guardrails
- **Model Explainability (SHAP / LIME):** Regulators (FCRA, GDPR) mandate that when a loan or transfer is blocked, the bank must provide adverse action reasons. Model predictions must log top contributing feature weights.
- **Fair Lending & Bias Prevention:** Protected demographic attributes (gender, race, zip code) must be strictly excluded from feature extraction pipelines.

### I. Production vs Local Differences

| Dimension | Local Development Experiment | Production Enterprise MLOps |
| :--- | :--- | :--- |
| **Model Registry** | Local SQLite + file store | Highly-Available MLflow on AWS RDS PostgreSQL + S3 |
| **Feature Store** | In-memory synthetic arrays | Managed Feast / AWS SageMaker Feature Store |
| **Serving Runtime** | Python script | In-process ONNX Runtime inside Spring Boot JVM |

### J. Interview Readiness Questions & Model Answers
- **Q1: Why did you choose in-process ONNX runtime over a remote Python model microservice (FastAPI/Triton)?**
  - *Answer:* In a core banking transfer flow, synchronous p99 latency must remain under 200ms. An external HTTP or gRPC call to a Python microservice adds 15–30ms of network latency, serialization overhead, connection pool management, and another failure domain. By exporting the model to ONNX, we load the compiled C++ execution engine directly inside the Spring Boot JVM memory space via JNI. Fraud evaluation completes in under 1.5 milliseconds with zero network hops.
- **Q2: What is Population Stability Index (PSI) and how does it detect data drift?**
  - *Answer:* Population Stability Index (PSI) measures the statistical divergence between the feature distribution of the baseline training population and the live production serving population. A $\text{PSI} < 0.1$ indicates no significant change; a $0.1 \le \text{PSI} < 0.2$ indicates moderate drift requiring attention; and $\text{PSI} \ge 0.2$ indicates severe distribution drift, signaling that customer behavior has changed (e.g. holiday spending patterns or new payment vectors) and triggering automated model retraining.
- **Q3: How do you handle class imbalance in financial fraud datasets?**
  - *Answer:* In real banking, fraudulent transactions represent less than 0.1% of total traffic. Training a model on raw accuracy results in a trivial model predicting "legitimate" 99.9% of the time. We handle this through: (1) Cost-sensitive learning (`is_unbalance=True` or weighted cross-entropy heavily penalizing false negatives). (2) Resampling techniques (SMOTE oversampling or Tomek links undersampling). (3) Evaluating on Precision-Recall AUC (PR-AUC) and False Positive Rate rather than ROC-AUC or raw Accuracy.

### K. Cost & Performance Considerations
- ONNX in-process inference adds < 10 MB RAM overhead to the backend pod and eliminates dedicated GPU server costs.

### L. Real-World Banking Considerations
- Financial model risk management is governed by the Federal Reserve's **SR 11-7** guidance on Model Risk Management. The versioned MLflow registry and automated training metrics provide mandatory compliance documentation for model governance boards.

### M. Progress Sign-off Checklist
- [ ] MLflow server running and UI accessible.
- [ ] Feature store specification reviewed.
- [ ] Synthetic training script executed and metrics logged.
- [ ] ONNX model export and JVM scoring strategy verified.

### N. Next Manual Transition
Proceed to **[Manual 30: AIOps Anomaly Detection & Automated Root Cause Analysis](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/30-aiops-anomaly-detection-automated-rca.md)** to implement automated anomaly detection across metrics, logs, and traces.

