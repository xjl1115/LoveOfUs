import request from '@/utils/request'
import type { Photo, PhotoDetail } from '@/types'

// 后端省份名称不带"市""省""自治区"后缀，前端选择器带后缀，此处统一去除
function normalizeProvince(name: string | undefined): string | undefined {
  if (!name) return undefined
  return name
    .replace(/^(北京|上海|天津|重庆)市$/, '$1')
    .replace(/^(香港|澳门)特别行政区$/, '$1')
    .replace(/^(广西壮族|宁夏回族|新疆维吾尔|内蒙古|西藏)自治区$/, '$1')
    .replace(/^(.*)省$/, '$1')
}

// 后端时间线响应中的单张照片
interface TimelinePhotoVO {
  id: number
  storagePath: string
  takenDate: string
  country?: string
  provence?: string
  city?: string
  locationName?: string
  description?: string
}

// 后端时间线响应中的分组
interface TimelineGroupVO {
  date: string
  photos: TimelinePhotoVO[]
}

// 后端时间线响应结构
interface TimelineResultVO {
  total: number
  page: number
  size: number
  records: TimelineGroupVO[]
}

export async function getTimelinePhotos(params: {
  page?: number
  size?: number
  province?: string
  startDate?: string
  endDate?: string
}) {
  const res = await request.get<TimelineResultVO>('/photos/timeline', {
    params: {
      ...params,
      province: normalizeProvince(params.province)
    }
  })

  // 将后端按月份组嵌套结构拍平成前端需要的 Photo[] 列表
  const list: Photo[] = []
  for (const group of res.records || []) {
    for (const photo of group.photos || []) {
      list.push({
        id: photo.id,
        userId: 0,
        storagePath: photo.storagePath?.trim(),
        takenDate: photo.takenDate,
        country: photo.country,
        province: photo.provence,
        city: photo.city,
        locationName: photo.locationName,
        description: photo.description,
        width: 0,
        height: 0,
        createdAt: photo.takenDate || ''
      })
    }
  }

  // 是否还有更多页
  const totalPages = Math.ceil(res.total / res.size)
  const hasMore = res.page < totalPages

  return { list, total: res.total, hasMore }
}

export function getPhotoDetail(id: number) {
  return request.get<PhotoDetail>(`/photos/${id}`)
}

export function uploadPhoto(data: FormData, onProgress?: (progress: number) => void) {
  return request.post<Photo>('/photos/upload', data, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (progressEvent) => {
      if (onProgress && progressEvent.total) {
        const progress = Math.round((progressEvent.loaded * 100) / progressEvent.total)
        onProgress(progress)
      }
    }
  })
}

export function updatePhoto(id: number, data: Partial<Photo>) {
  return request.put<Photo>(`/photos/${id}`, data)
}

export function deletePhoto(id: number) {
  return request.delete<void>(`/photos/${id}`)
}

export function getPhotosByProvince(province: string) {
  return request.get<Photo[]>(`/photos/province/${province}`)
}