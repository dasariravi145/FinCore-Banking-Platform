import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { customerApi } from '../../api/customers';
import { Card } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { StatusBadge } from '../../components/common/StatusBadge';
import { TableSkeleton } from '../../components/common/Skeleton';
import { EmptyState } from '../../components/common/EmptyState';
import { formatDate } from '../../utils/formatters';
import { Users } from 'lucide-react';

export const AdminCustomersPage: React.FC = () => {
  const [page, setPage] = useState(0);
  const size = 15;

  const { data: customersPage, isLoading } = useQuery({
    queryKey: ['admin', 'customers', page, size],
    queryFn: () => customerApi.getAllCustomers(page, size),
  });

  const customers = customersPage?.content || [];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Customer Registry</h1>
        <p className="text-xs sm:text-sm text-slate-500 mt-0.5">
          Administrative oversight of verified banking customer identities and KYC records
        </p>
      </div>

      <Card title="Registered Customers">
        {isLoading ? (
          <TableSkeleton rows={6} cols={6} />
        ) : customers.length === 0 ? (
          <EmptyState
            icon={<Users className="w-8 h-8 text-slate-400" />}
            title="No Customers Registered"
            description="No customer KYC profiles found in the platform database."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50 text-slate-600 uppercase tracking-wider border-b border-slate-200">
                <tr>
                  <th className="py-3 px-4 font-semibold">Customer No</th>
                  <th className="py-3 px-4 font-semibold">Customer Name</th>
                  <th className="py-3 px-4 font-semibold">Email</th>
                  <th className="py-3 px-4 font-semibold">Phone</th>
                  <th className="py-3 px-4 font-semibold">City / Country</th>
                  <th className="py-3 px-4 font-semibold">Joined Date</th>
                  <th className="py-3 px-4 font-semibold text-center">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {customers.map((cust) => (
                  <tr key={cust.id} className="hover:bg-slate-50/60 transition">
                    <td className="py-3 px-4 font-mono font-bold text-slate-900">
                      {cust.customerNumber}
                    </td>
                    <td className="py-3 px-4 font-semibold text-slate-900">
                      {cust.firstName} {cust.lastName}
                    </td>
                    <td className="py-3 px-4 text-slate-600">{cust.email}</td>
                    <td className="py-3 px-4 font-mono text-slate-600">{cust.phoneNumber}</td>
                    <td className="py-3 px-4 text-slate-700">
                      {cust.city}, {cust.country}
                    </td>
                    <td className="py-3 px-4 text-slate-500">{formatDate(cust.createdAt)}</td>
                    <td className="py-3 px-4 text-center">
                      <StatusBadge status={cust.status} size="sm" />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {customersPage && customersPage.totalPages > 1 && (
              <div className="flex items-center justify-between pt-4 mt-2 border-t border-slate-100 text-xs text-slate-500">
                <span>
                  Page {customersPage.number + 1} of {customersPage.totalPages} ({customersPage.totalElements} records)
                </span>
                <div className="flex gap-2">
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={customersPage.first}
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                  >
                    Previous
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={customersPage.last}
                    onClick={() => setPage((p) => p + 1)}
                  >
                    Next
                  </Button>
                </div>
              </div>
            )}
          </div>
        )}
      </Card>
    </div>
  );
};
