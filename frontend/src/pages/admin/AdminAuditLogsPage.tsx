import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { auditApi } from '../../api/audit';
import { Card } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { TableSkeleton } from '../../components/common/Skeleton';
import { EmptyState } from '../../components/common/EmptyState';
import { formatDateTime } from '../../utils/formatters';
import { FileText } from 'lucide-react';

export const AdminAuditLogsPage: React.FC = () => {
  const [page, setPage] = useState(0);
  const size = 20;

  const { data: auditPage, isLoading } = useQuery({
    queryKey: ['admin', 'audit-logs', page, size],
    queryFn: () => auditApi.getAuditLogs(page, size),
  });

  const auditLogs = auditPage?.content || [];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Security Audit Logs</h1>
        <p className="text-xs sm:text-sm text-slate-500 mt-0.5">
          Chronological audit trail of all authentication events, administrative overrides, and financial operations
        </p>
      </div>

      <Card title="Institutional Audit Trail">
        {isLoading ? (
          <TableSkeleton rows={8} cols={6} />
        ) : auditLogs.length === 0 ? (
          <EmptyState
            icon={<FileText className="w-8 h-8 text-slate-400" />}
            title="No Audit Logs Recorded"
            description="The security audit log is currently empty."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50 text-slate-600 uppercase tracking-wider border-b border-slate-200">
                <tr>
                  <th className="py-3 px-4 font-semibold">Timestamp</th>
                  <th className="py-3 px-4 font-semibold">Event Type</th>
                  <th className="py-3 px-4 font-semibold">Principal / User</th>
                  <th className="py-3 px-4 font-semibold">Client IP</th>
                  <th className="py-3 px-4 font-semibold">Details / Context</th>
                  <th className="py-3 px-4 font-semibold text-center">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {auditLogs.map((log) => (
                  <tr key={log.id} className="hover:bg-slate-50/60 transition">
                    <td className="py-3 px-4 font-mono text-slate-600 whitespace-nowrap">
                      {formatDateTime(log.createdAt)}
                    </td>
                    <td className="py-3 px-4 font-mono font-semibold text-slate-900">
                      {log.eventType}
                    </td>
                    <td className="py-3 px-4 font-medium text-slate-700">{log.principal}</td>
                    <td className="py-3 px-4 font-mono text-slate-500">{log.ipAddress}</td>
                    <td className="py-3 px-4 text-slate-600 max-w-sm truncate">
                      {log.details || '—'}
                    </td>
                    <td className="py-3 px-4 text-center">
                      <span
                        className={`inline-block px-2 py-0.5 rounded text-[11px] font-bold ${
                          log.status === 'SUCCESS'
                            ? 'bg-emerald-50 text-emerald-700'
                            : 'bg-rose-50 text-rose-700'
                        }`}
                      >
                        {log.status}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {auditPage && auditPage.totalPages > 1 && (
              <div className="flex items-center justify-between pt-4 mt-2 border-t border-slate-100 text-xs text-slate-500">
                <span>
                  Page {auditPage.number + 1} of {auditPage.totalPages} ({auditPage.totalElements} records)
                </span>
                <div className="flex gap-2">
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={auditPage.first}
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                  >
                    Previous
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={auditPage.last}
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
