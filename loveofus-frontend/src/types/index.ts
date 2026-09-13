// 用户类型
export interface UserInfo {
  id: number
  nickname: string
  avatarUrl?: string
  phone?: string
  email?: string
  partnerId?: number
  partner?: UserInfo
  relationshipStart?: string
  isBound: boolean
  /** 性别：0-未知/未设置，1-男，2-女 */
  gender?: number
  /** VIP 等级：0-普通用户，1-周卡，2-月卡，3-季卡，4-年卡，5-永久 */
  vipLevel?: number
  /** VIP 等级名称 */
  vipLevelName?: string
  /** VIP 到期时间，未开通或永久卡为空 */
  vipExpireAt?: string | null
}

// 照片类型
export interface Photo {
  id: number
  userId: number
  storagePath?: string
  thumbnailUrl?: string
  smallUrl?: string
  mediumUrl?: string
  originalUrl?: string
  width?: number
  height?: number
  takenDate?: string
  takenTime?: string
  locationName?: string
  city?: string
  province?: string
  country?: string
  description?: string
  aiTags?: string[]
  createdAt: string
}

export interface PhotoDetail extends Photo {
  fileSize: number
  mimeType: string
  latitude?: number
  longitude?: number
  country?: string
  aiDescription?: string
  aiEmotion?: string
  tags: Tag[]
}

// 相册类型
export interface Album {
  id: number
  groupId: number
  name: string
  coverPhotoUrl?: string
  description?: string
  isAiGenerated?: boolean
  photoCount: number
  createdAt: string
  updatedAt?: string
}

// 相册详情中的照片
export interface AlbumPhoto {
  id: number
  storagePath: string
  takenDate: string
  locationName?: string
  city?: string
  province?: string
  country?: string
  description?: string
  userId: number
  userNickname: string
}

// 相册详情（含照片列表+分页）
export interface AlbumDetail extends Album {
  photos: AlbumPhoto[]
  page: {
    current: number
    size: number
    total: number
    pages: number
  }
}

// 标签类型
export interface Tag {
  id: number
  name: string
  type: 'manual' | 'ai'
}

// 时间线类型
export interface TimelineGroup {
  month: string
  monthLabel: string
  photos: Photo[]
}

// 省份数据类型
export interface ProvinceData {
  name: string
  count: number
  takenDate?: string | { year: number; month: number; day: number } | null
}

// 用户统计类型
export interface UserStats {
  photoCount: number
  albumCount: number
  cityCount: number
  daysTogether: number
  cities: ProvinceData[]
  monthlyTimeline: { month: string; count: number }[]
}

// 导出记录类型
export interface ExportRecord {
  id: number
  startDate: string
  endDate: string
  photoCount: number
  format: 'zip' | 'pdf'
  status: 'pending' | 'processing' | 'completed' | 'failed'
  filePath?: string
  fileSize?: number
  createdAt: string
  completedAt?: string
}

// 上传任务类型
export interface UploadTask {
  id: string
  file: File
  progress: number
  status: 'pending' | 'uploading' | 'success' | 'error'
  errorMessage?: string
}

// 日期范围类型
export interface DateRange {
  startDate: string
  endDate: string
}
