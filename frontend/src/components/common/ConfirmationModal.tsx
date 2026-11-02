import React, { ReactNode } from 'react';
import { Modal } from './Modal';
import { Button } from './Button';
import { AlertTriangle, Info, CheckCircle2 } from 'lucide-react';

export interface ConfirmationModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  description: string;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: 'danger' | 'warning' | 'primary' | 'secondary';
  isLoading?: boolean;
  children?: ReactNode;
}

export const ConfirmationModal: React.FC<ConfirmationModalProps> = ({
  isOpen,
  onClose,
  onConfirm,
  title,
  description,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  variant = 'primary',
  isLoading = false,
  children,
}) => {
  const iconMap = {
    danger: <AlertTriangle className="w-6 h-6 text-rose-600" />,
    warning: <AlertTriangle className="w-6 h-6 text-amber-600" />,
    primary: <Info className="w-6 h-6 text-navy-900" />,
    secondary: <CheckCircle2 className="w-6 h-6 text-royal-600" />,
  };

  const bgMap = {
    danger: 'bg-rose-50',
    warning: 'bg-amber-50',
    primary: 'bg-navy-50',
    secondary: 'bg-royal-50',
  };

  const buttonVariantMap = {
    danger: 'danger' as const,
    warning: 'secondary' as const,
    primary: 'primary' as const,
    secondary: 'secondary' as const,
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={title}>
      <div className="space-y-4">
        <div className="flex items-start gap-4">
          <div className={`p-3 rounded-xl flex-shrink-0 ${bgMap[variant]}`}>
            {iconMap[variant]}
          </div>
          <div>
            <p className="text-sm text-slate-600 leading-relaxed">{description}</p>
          </div>
        </div>

        {children && <div className="mt-4 pt-4 border-t border-slate-100">{children}</div>}

        <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-100">
          <Button variant="outline" onClick={onClose} disabled={isLoading}>
            {cancelLabel}
          </Button>
          <Button
            variant={buttonVariantMap[variant]}
            onClick={onConfirm}
            isLoading={isLoading}
          >
            {confirmLabel}
          </Button>
        </div>
      </div>
    </Modal>
  );
};

