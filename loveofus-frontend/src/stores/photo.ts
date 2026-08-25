import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Photo, PhotoDetail, TimelineGroup, UploadTask } from '@/types'
import { formatMonthLabel } from '@/utils/date'

export const usePhotoStore = defineStore('photo', () => {
  // State
  const photos = ref<Photo[]>([])
  const currentPhoto = ref<PhotoDetail | null>(null)
  const currentPage = ref(1)
  const hasMore = ref(true)
  const loading = ref(false)
  const uploadQueue = ref<UploadTask[]>([])
  const activeProvince = ref<string | null>(null)

  // Getters
  const timelineGroups = computed<TimelineGroup[]>(() => {
    const groups: Record<string, Photo[]> = {}

    photos.value.forEach(photo => {
      const month = photo.takenDate?.substring(0, 7) || photo.createdAt?.substring(0, 7)
      if (!month) return
      if (!groups[month]) {
        groups[month] = []
      }
      groups[month].push(photo)
    })

    return Object.entries(groups)
      .sort(([a], [b]) => b.localeCompare(a))
      .map(([month, photos]) => ({
        month,
        monthLabel: formatMonthLabel(month),
        photos: photos.sort((a, b) =>
          (b.takenDate || b.createdAt).localeCompare(a.takenDate || a.createdAt)
        )
      }))
  })

  const filteredPhotos = computed(() => {
    if (activeProvince.value) {
      return photos.value.filter(p => p.province === activeProvince.value)
    }
    return photos.value
  })

  // Actions
  function setPhotos(newPhotos: Photo[], reset = false) {
    if (reset) {
      photos.value = newPhotos
      currentPage.value = 1
    } else {
      photos.value.push(...newPhotos)
    }
  }

  function setCurrentPhoto(photo: PhotoDetail | null) {
    currentPhoto.value = photo
  }

  function setHasMore(value: boolean) {
    hasMore.value = value
  }

  function setLoading(value: boolean) {
    loading.value = value
  }

  function incrementPage() {
    currentPage.value++
  }

  function setActiveProvince(province: string | null) {
    activeProvince.value = province
  }

  function addUploadTask(task: UploadTask) {
    uploadQueue.value.push(task)
  }

  function updateUploadTask(id: string, updates: Partial<UploadTask>) {
    const task = uploadQueue.value.find(t => t.id === id)
    if (task) {
      Object.assign(task, updates)
    }
  }

  function removeUploadTask(id: string) {
    const index = uploadQueue.value.findIndex(t => t.id === id)
    if (index > -1) {
      uploadQueue.value.splice(index, 1)
    }
  }

  function clearUploadQueue() {
    uploadQueue.value = []
  }

  return {
    photos,
    currentPhoto,
    currentPage,
    hasMore,
    loading,
    uploadQueue,
    activeProvince,
    timelineGroups,
    filteredPhotos,
    setPhotos,
    setCurrentPhoto,
    setHasMore,
    setLoading,
    incrementPage,
    setActiveProvince,
    addUploadTask,
    updateUploadTask,
    removeUploadTask,
    clearUploadQueue
  }
})
