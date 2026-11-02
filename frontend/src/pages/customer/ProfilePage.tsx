import React, { useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useAuth } from '../../context/AuthContext';
import { customerApi, CreateCustomerPayload, UpdateCustomerPayload } from '../../api/customers';
import { useToast } from '../../context/ToastContext';
import { getErrorMessage } from '../../utils/errors';
import { Card } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { Input } from '../../components/common/Input';
import { StatusBadge } from '../../components/common/StatusBadge';
import { Skeleton } from '../../components/common/Skeleton';
import { formatDate } from '../../utils/formatters';
import { Phone, MapPin } from 'lucide-react';

const kycSchema = z.object({
  firstName: z.string().min(1, 'First name is required'),
  lastName: z.string().min(1, 'Last name is required'),
  phoneNumber: z.string().regex(/^\+[1-9]\d{1,14}$/, 'Format: +919876543210'),
  dateOfBirth: z.string().min(1, 'Date of birth is required'),
  addressLine1: z.string().min(1, 'Address is required'),
  addressLine2: z.string().optional(),
  city: z.string().min(1, 'City is required'),
  state: z.string().min(1, 'State is required'),
  postalCode: z.string().min(1, 'Postal code is required'),
  country: z.string().min(1, 'Country is required'),
  nationalId: z.string().min(1, 'National ID is required'),
});

const updateProfileSchema = z.object({
  phoneNumber: z.string().regex(/^\+[1-9]\d{1,14}$/, 'Format: +919876543210'),
  addressLine1: z.string().min(1, 'Address is required'),
  addressLine2: z.string().optional(),
  city: z.string().min(1, 'City is required'),
  state: z.string().min(1, 'State is required'),
  postalCode: z.string().min(1, 'Postal code is required'),
  country: z.string().min(1, 'Country is required'),
});

type KycFormData = z.infer<typeof kycSchema>;
type UpdateProfileFormData = z.infer<typeof updateProfileSchema>;

export const ProfilePage: React.FC = () => {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const { success: toastSuccess, error: toastError } = useToast();

  const { data: profile, isLoading } = useQuery({
    queryKey: ['customers', 'me'],
    queryFn: () => customerApi.getMyProfile(),
    retry: 1,
  });

  // KYC Onboarding Form
  const {
    register: registerKyc,
    handleSubmit: handleSubmitKyc,
    formState: { errors: kycErrors },
  } = useForm<KycFormData>({
    resolver: zodResolver(kycSchema),
    defaultValues: {
      firstName: user?.firstName || '',
      lastName: user?.lastName || '',
      phoneNumber: user?.phoneNumber || '+919876543210',
      dateOfBirth: '1992-08-15',
      addressLine1: '123 Marine Drive',
      city: 'Mumbai',
      state: 'Maharashtra',
      postalCode: '400020',
      country: 'India',
      nationalId: 'SYNTH-PAN-12345',
    },
  });

  // Update Profile Form
  const {
    register: registerUpdate,
    handleSubmit: handleSubmitUpdate,
    setValue: setUpdateValue,
    formState: { errors: updateErrors },
  } = useForm<UpdateProfileFormData>({
    resolver: zodResolver(updateProfileSchema),
  });

  useEffect(() => {
    if (profile) {
      setUpdateValue('phoneNumber', profile.phoneNumber);
      setUpdateValue('addressLine1', profile.addressLine1);
      setUpdateValue('addressLine2', profile.addressLine2 || '');
      setUpdateValue('city', profile.city);
      setUpdateValue('state', profile.state);
      setUpdateValue('postalCode', profile.postalCode);
      setUpdateValue('country', profile.country);
    }
  }, [profile, setUpdateValue]);

  const createMutation = useMutation({
    mutationFn: (data: CreateCustomerPayload) => customerApi.createProfile(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customers', 'me'] });
      toastSuccess('Customer Profile Created', 'Your KYC profile is now registered.');
    },
    onError: (err) => {
      toastError('Failed to create profile', getErrorMessage(err));
    },
  });

  const updateMutation = useMutation({
    mutationFn: (data: UpdateCustomerPayload) => customerApi.updateMyProfile(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customers', 'me'] });
      toastSuccess('Profile Updated', 'Contact information updated successfully.');
    },
    onError: (err) => {
      toastError('Failed to update profile', getErrorMessage(err));
    },
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Customer Profile & KYC</h1>
        <p className="text-xs sm:text-sm text-slate-500 mt-0.5">
          Verified customer identity details and legal address
        </p>
      </div>

      {isLoading ? (
        <Skeleton className="h-64 w-full" />
      ) : profile ? (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Readonly Identity Overview */}
          <Card title="Customer Identity" className="lg:col-span-1">
            <div className="space-y-4 text-xs">
              <div>
                <span className="text-slate-500">Customer Number:</span>
                <p className="font-mono font-bold text-slate-900 mt-0.5">
                  {profile.customerNumber}
                </p>
              </div>
              <div>
                <span className="text-slate-500">Full Legal Name:</span>
                <p className="font-semibold text-slate-900 mt-0.5">
                  {profile.firstName} {profile.lastName}
                </p>
              </div>
              <div>
                <span className="text-slate-500">Email Address:</span>
                <p className="text-slate-900 mt-0.5">{profile.email}</p>
              </div>
              <div>
                <span className="text-slate-500">Date of Birth:</span>
                <p className="text-slate-900 mt-0.5">{formatDate(profile.dateOfBirth)}</p>
              </div>
              <div>
                <span className="text-slate-500">National ID / PAN:</span>
                <p className="font-mono text-slate-900 mt-0.5">{profile.nationalId}</p>
              </div>
              <div>
                <span className="text-slate-500">Account Status:</span>
                <div className="mt-0.5">
                  <StatusBadge status={profile.status} size="sm" />
                </div>
              </div>
            </div>
          </Card>

          {/* Updateable Contact Details Form */}
          <Card title="Update Contact & Address" className="lg:col-span-2">
            <form onSubmit={handleSubmitUpdate((d) => updateMutation.mutate(d))} className="space-y-4">
              <Input
                label="Phone Number"
                leftIcon={<Phone className="w-4 h-4" />}
                error={updateErrors.phoneNumber?.message}
                {...registerUpdate('phoneNumber')}
              />

              <div className="grid grid-cols-2 gap-3">
                <Input
                  label="Address Line 1"
                  leftIcon={<MapPin className="w-4 h-4" />}
                  error={updateErrors.addressLine1?.message}
                  {...registerUpdate('addressLine1')}
                />
                <Input
                  label="Address Line 2 (Optional)"
                  error={updateErrors.addressLine2?.message}
                  {...registerUpdate('addressLine2')}
                />
              </div>

              <div className="grid grid-cols-3 gap-3">
                <Input
                  label="City"
                  error={updateErrors.city?.message}
                  {...registerUpdate('city')}
                />
                <Input
                  label="State"
                  error={updateErrors.state?.message}
                  {...registerUpdate('state')}
                />
                <Input
                  label="Postal Code"
                  error={updateErrors.postalCode?.message}
                  {...registerUpdate('postalCode')}
                />
              </div>

              <Input
                label="Country"
                error={updateErrors.country?.message}
                {...registerUpdate('country')}
              />

              <div className="flex justify-end pt-2">
                <Button
                  type="submit"
                  variant="primary"
                  isLoading={updateMutation.isPending}
                >
                  Save Profile Changes
                </Button>
              </div>
            </form>
          </Card>
        </div>
      ) : (
        /* KYC Onboarding Form if Profile Not Found */
        <Card title="Register Customer KYC Profile">
          <form onSubmit={handleSubmitKyc((d) => createMutation.mutate(d))} className="space-y-4">
            <div className="p-3 bg-amber-50 border border-amber-200 rounded-lg text-xs text-amber-800">
              Please submit synthetic customer identity information to initialize your customer banking account.
            </div>

            <div className="grid grid-cols-2 gap-3">
              <Input
                label="First Name"
                error={kycErrors.firstName?.message}
                {...registerKyc('firstName')}
              />
              <Input
                label="Last Name"
                error={kycErrors.lastName?.message}
                {...registerKyc('lastName')}
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <Input
                label="Phone Number"
                error={kycErrors.phoneNumber?.message}
                {...registerKyc('phoneNumber')}
              />
              <Input
                label="Date of Birth"
                type="date"
                error={kycErrors.dateOfBirth?.message}
                {...registerKyc('dateOfBirth')}
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <Input
                label="Address Line 1"
                error={kycErrors.addressLine1?.message}
                {...registerKyc('addressLine1')}
              />
              <Input
                label="Address Line 2 (Optional)"
                error={kycErrors.addressLine2?.message}
                {...registerKyc('addressLine2')}
              />
            </div>

            <div className="grid grid-cols-3 gap-3">
              <Input
                label="City"
                error={kycErrors.city?.message}
                {...registerKyc('city')}
              />
              <Input
                label="State"
                error={kycErrors.state?.message}
                {...registerKyc('state')}
              />
              <Input
                label="Postal Code"
                error={kycErrors.postalCode?.message}
                {...registerKyc('postalCode')}
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <Input
                label="Country"
                error={kycErrors.country?.message}
                {...registerKyc('country')}
              />
              <Input
                label="Synthetic National ID (PAN)"
                error={kycErrors.nationalId?.message}
                {...registerKyc('nationalId')}
              />
            </div>

            <div className="flex justify-end pt-3 border-t border-slate-100">
              <Button
                type="submit"
                variant="primary"
                isLoading={createMutation.isPending}
              >
                Submit Customer Profile
              </Button>
            </div>
          </form>
        </Card>
      )}
    </div>
  );
};
