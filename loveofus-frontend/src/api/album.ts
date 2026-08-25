import request from '@/utils/request'
import type { Album, AlbumDetail, Photo } from '@/types'

// 获取相册列表
export function getAlbums() {
  return request.get<Album[]>('/albums')
}

// 获取相册详情（含照片列表）
export function getAlbumDetail(id: number) {
  return request.get<AlbumDetail>(`/albums/${id}`)
}

// 获取相册内的照片
export function getAlbumPhotos(id: number, params?: { page?: number; size?: number }) {
  return request.get<{
    list: Photo[]
    total: number
    hasMore: boolean
  }>(`/albums/${id}/photos`, { params })
}

// 创建相册
export function createAlbum(data: { name: string; description?: string }) {
  return request.post<Album>('/albums', data)
}

// 更新相册
export function updateAlbum(id: number, data: Partial<Album>) {
  return request.put<Album>(`/albums/${id}`, data)
}

// 删除相册
export function deleteAlbum(id: number) {
  return request.delete<void>(`/albums/${id}`)
}

// 添加照片到相册
export function addPhotosToAlbum(albumId: number, photoIds: number[]) {
  return request.post<void>(`/albums/${albumId}/photos`, { photoIds })
}

// 从相册移除照片
export function removePhotoFromAlbum(albumId: number, photoId: number) {
  return request.delete<void>(`/albums/${albumId}/photos/${photoId}`)
}
