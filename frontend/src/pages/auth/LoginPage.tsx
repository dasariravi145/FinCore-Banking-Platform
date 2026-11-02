import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { authApi } from '../../api/auth';
import { useToast } from '../../context/ToastContext';
import { getErrorMessage } from '../../utils/errors';
import { Input } from '../../components/common/Input';
import { Button } from '../../components/common/Button';
import { Building2, Lock, Mail, ShieldAlert } from 'lucide-react';

const loginSchema = z.object({
  email: z.string().email('Please enter a valid email address'),
  password: z.string().min(1, 'Password is required'),
});

type LoginFormData = z.infer<typeof loginSchema>;

export const LoginPage: React.FC = () => {
  const { login } = useAuth();
  const { error: toastError, success: toastSuccess } = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const [isLoading, setIsLoading] = useState(false);

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors },
  } = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
  });

  const onSubmit = async (data: LoginFormData) => {
    setIsLoading(true);
    try {
      const response = await authApi.login(data);
      login(response);
      toastSuccess('Signed in successfully', `Welcome back, ${response.user.firstName}!`);

      // Redirect based on role or previous location
      const from = (location.state as { from?: { pathname: string } })?.from?.pathname;
      if (from) {
        navigate(from, { replace: true });
      } else if (response.user.roles.includes('ROLE_ADMIN')) {
        navigate('/admin', { replace: true });
      } else {
        navigate('/dashboard', { replace: true });
      }
    } catch (err) {
      toastError('Authentication failed', getErrorMessage(err));
    } finally {
      setIsLoading(false);
    }
  };

  const fillDemoAdmin = () => {
    setValue('email', 'admin@fincore.local');
    setValue('password', 'AdminSecret2026!');
  };

  const fillDemoCustomer = () => {
    setValue('email', 'customer@fincore.local');
    setValue('password', 'CustomerSecret2026!');
  };

  return (
    <div className="min-h-screen bg-slate-900 flex flex-col justify-center py-12 sm:px-6 lg:px-8 relative overflow-hidden">
      {/* Background radial gradient */}
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-navy-800 via-navy-950 to-slate-950 opacity-90" />

      <div className="sm:mx-auto sm:w-full sm:max-w-md relative z-10">
        <div className="flex justify-center">
          <div className="w-12 h-12 rounded-xl bg-gradient-to-tr from-gold-500 to-amber-300 flex items-center justify-center text-navy-950 font-bold shadow-lg">
            <Building2 className="w-7 h-7 text-navy-950" />
          </div>
        </div>
        <h2 className="mt-4 text-center text-2xl font-bold tracking-tight text-white">
          FinCore Banking Platform
        </h2>
        <p className="mt-1 text-center text-xs text-slate-400">
          Enterprise Financial Ledger & Core Banking Simulation
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md relative z-10 px-4 sm:px-0">
        <div className="bg-white py-8 px-6 shadow-2xl rounded-2xl sm:px-10 border border-slate-100">
          <form className="space-y-5" onSubmit={handleSubmit(onSubmit)}>
            <Input
              label="Email Address"
              type="email"
              placeholder="e.g. name@example.com"
              leftIcon={<Mail className="w-4 h-4" />}
              error={errors.email?.message}
              {...register('email')}
            />

            <Input
              label="Password"
              type="password"
              placeholder="••••••••••••"
              leftIcon={<Lock className="w-4 h-4" />}
              error={errors.password?.message}
              {...register('password')}
            />

            <Button
              type="submit"
              variant="primary"
              size="lg"
              className="w-full mt-2"
              isLoading={isLoading}
            >
              Sign In to Banking Portal
            </Button>
          </form>

          {/* Demo Helper Presets (Safely isolated to local development environments only) */}
          {import.meta.env.DEV && (
            <div className="mt-6 pt-6 border-t border-slate-100">
              <div className="flex items-center gap-1.5 text-xs text-slate-500 mb-2">
                <ShieldAlert className="w-3.5 h-3.5 text-amber-500" />
                <span>Local Dev Demo Presets:</span>
              </div>
              <div className="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  onClick={fillDemoAdmin}
                  className="text-[11px] font-medium py-1.5 px-2.5 rounded-lg border border-slate-200 hover:bg-slate-50 text-slate-700 transition"
                >
                  Preset Admin
                </button>
                <button
                  type="button"
                  onClick={fillDemoCustomer}
                  className="text-[11px] font-medium py-1.5 px-2.5 rounded-lg border border-slate-200 hover:bg-slate-50 text-slate-700 transition"
                >
                  Preset Customer
                </button>
              </div>
            </div>
          )}

          <div className="mt-6 text-center">
            <p className="text-xs text-slate-600">
              Need a new customer account?{' '}
              <Link to="/register" className="font-semibold text-royal-600 hover:text-royal-500">
                Register here
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

