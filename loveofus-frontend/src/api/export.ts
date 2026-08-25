import request from '@/utils/request'
import type { ExportRecord } from '@/types'

// 创建导出任务
export function createExport(data: {
  startDate: string
  endDate: string
  format: 'zip' | 'pdf' | 'video'
  options?: Record<string, any>
}) {
  return request.post<ExportRecord>('/exports', data)
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
