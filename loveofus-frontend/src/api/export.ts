import request from '@/utils/request'
import type { ExportRecord } from '@/types'

// 创建导出任务
export function createExport(data: {
  startDate: string
  endDate: string
  format: 'zip' | 'pdf'
  exportType?: 'all' | 'date' | 'selected' | 'album'
  groupBy?: 'none' | 'takenDate' | 'createdAt'
  photoIds?: number[]
  albumId?: number
  options?: Record<string, any>
}) {
  // 默认按日期范围筛选，与页面交互一致
  const payload = { exportType: 'date', ...data }
  return request.post<ExportRecord>('/exports', payload)
}

// 获取导出历史
export function getExportHistory() {
  return request.get<ExportRecord[]>('/exports')
}

// 获取导出任务状态
export function getExportStatus(id: number) {
  return request.get<ExportRecord>(`/exports/${id}/status`)
}

// 取消导出任务
export function cancelExport(id: number) {
  return request.post<void>(`/exports/${id}/cancel`)
}

// 下载导出文件
export function downloadExport(id: number) {
  return request.get<Blob>(`/exports/${id}/download`, {
    responseType: 'blob'
  })
}
