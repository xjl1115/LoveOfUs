<template>
  <div class="map-section">
    <div class="map-header" @click="toggleExpand">
      <h3>🗺️ 我们去过的地方</h3>
      <div class="map-header-right">
        <span class="map-progress">
          已打卡 <em>{{ totalVisited }}</em> / {{ totalProvinces }} 个省份
        </span>
        <van-icon :name="isExpanded ? 'arrow-up' : 'arrow-down'" />
      </div>
    </div>

    <div v-show="isExpanded" class="map-content">
      <div v-if="mapLoading" class="map-loading">
        <van-loading type="spinner" size="24" />
        <span>正在加载地图...</span>
      </div>
      <div v-else-if="mapError" class="map-error">
        <van-icon name="warning-o" size="32" />
        <span>地图加载失败</span>
        <van-button size="small" @click="retryLoadMap">重试</van-button>
      </div>
      <div v-else ref="chartRef" class="china-map norem"></div>
      <div v-if="mapDataLoaded && !mapError" class="map-legend">
        <span class="legend-item"><i style="background:#E8E8E8"></i> 未去过</span>
        <span class="legend-item"><i style="background:#FF8A65"></i> 去过</span>
        <span class="legend-item"><i style="background:#BF360C"></i> 照片多</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import type { ProvinceData } from '@/types'

interface Props {
  provinceData: ProvinceData[]
  activeProvince?: string | null
}

const props = withDefaults(defineProps<Props>(), {
  activeProvince: null
})

const emit = defineEmits<{
  filterByProvince: [province: string | null]
}>()

const chartRef = ref<HTMLDivElement>()
const totalVisited = ref(0)
const totalProvinces = 34
const isExpanded = ref(true)
const mapLoading = ref(true)
const mapError = ref(false)
const mapDataLoaded = ref(false)

// 后端简称 → ECharts 全称映射
const provinceNameMap: Record<string, string> = {
  '北京': '北京市', '天津': '天津市', '上海': '上海市', '重庆': '重庆市',
  '河北': '河北省', '山西': '山西省', '辽宁': '辽宁省', '吉林': '吉林省',
  '黑龙江': '黑龙江省', '江苏': '江苏省', '浙江': '浙江省', '安徽': '安徽省',
  '福建': '福建省', '江西': '江西省', '山东': '山东省', '河南': '河南省',
  '湖北': '湖北省', '湖南': '湖南省', '广东': '广东省', '海南': '海南省',
  '四川': '四川省', '贵州': '贵州省', '云南': '云南省', '陕西': '陕西省',
  '甘肃': '甘肃省', '青海': '青海省', '台湾': '台湾省',
  '内蒙古': '内蒙古自治区', '广西': '广西壮族自治区', '西藏': '西藏自治区',
  '宁夏': '宁夏回族自治区', '新疆': '新疆维吾尔自治区',
  '香港': '香港特别行政区', '澳门': '澳门特别行政区'
}
// 全称 → 简称反向映射
const reverseNameMap: Record<string, string> = Object.fromEntries(
  Object.entries(provinceNameMap).map(([short, full]) => [full, short])
)

let myChart: any = null
let echarts: any = null
let renderingTimer: ReturnType<typeof setTimeout> | null = null
let resizeObserver: ResizeObserver | null = null
let resizeDebounceTimer: ReturnType<typeof setTimeout> | null = null

const currentActiveProvince = ref<string | null>(props.activeProvince)

let pendingProvinceData: ProvinceData[] = []

// GeoJSON 加载后提取的 feature name 集合，用于智能匹配
let geoFeatureNames: Set<string> = new Set()

watch(() => props.provinceData, (newData) => {
  totalVisited.value = newData.length
  pendingProvinceData = [...newData]
  if (myChart) {
    renderChart()
  }
}, { deep: true })

watch(() => props.activeProvince, (newVal) => {
  currentActiveProvince.value = newVal
  if (myChart) {
    myChart.dispatchAction({ type: 'downplay' })
    if (newVal) {
      myChart.dispatchAction({ type: 'highlight', name: newVal })
    }
  }
})

onMounted(() => {
  totalVisited.value = props.provinceData.length
  pendingProvinceData = [...props.provinceData]
  loadChinaMap()
})

onUnmounted(() => {
  if (renderingTimer) clearTimeout(renderingTimer)
  if (resizeDebounceTimer) clearTimeout(resizeDebounceTimer)
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  if (myChart) {
    myChart.dispose()
    myChart = null
  }
  window.removeEventListener('resize', handleResize)
})

async function loadChinaMap() {
  const echartsModule = await import('echarts')
  echarts = echartsModule

  mapLoading.value = true
  mapError.value = false

  const mapUrls = [
    'https://geo.datav.aliyun.com/areas_v3/bound/100000_full.json',
    'https://fastly.jsdelivr.net/npm/echarts/map/json/china.json',
    'https://cdn.jsdelivr.net/npm/echarts@5.4.3/map/json/china.json'
  ]

  for (const url of mapUrls) {
    try {
      const response = await fetch(url, {
        method: 'GET',
        mode: 'cors',
        headers: { 'Accept': 'application/json' }
      })
      if (!response.ok) continue

      const chinaGeoJson = await response.json()
      if (!chinaGeoJson || (!chinaGeoJson.features && !chinaGeoJson.type)) continue

      // 提取 GeoJSON 中所有 feature name，用于后续智能匹配
      geoFeatureNames = new Set(
        (chinaGeoJson.features || []).map((f: any) => f.properties?.name).filter(Boolean)
      )

      echarts.registerMap('china', chinaGeoJson)
      mapLoading.value = false
      mapDataLoaded.value = true

      await nextTick()
      initChart()
      return
    } catch (error) {
      continue
    }
  }

  mapLoading.value = false
  mapError.value = true
  mapDataLoaded.value = false
}

function retryLoadMap() {
  loadChinaMap()
}

function toggleExpand() {
  isExpanded.value = !isExpanded.value
  if (isExpanded.value && myChart) {
    setTimeout(() => {
      myChart?.resize()
      renderChart()
    }, 350)
  }
}

function initChart() {
  if (!chartRef.value || myChart) return

  try {
    myChart = echarts.init(chartRef.value)
    window.addEventListener('resize', handleResize)

    // ResizeObserver 监听容器尺寸变化，带防抖
    if (chartRef.value) {
      resizeObserver = new ResizeObserver(() => {
        if (resizeDebounceTimer) clearTimeout(resizeDebounceTimer)
        resizeDebounceTimer = setTimeout(() => {
          myChart?.resize()
        }, 100)
      })
      resizeObserver.observe(chartRef.value)
    }

    if (pendingProvinceData.length > 0) {
      renderChart()
    } else {
      renderChart()
    }

    if (renderingTimer) clearTimeout(renderingTimer)
    renderingTimer = setTimeout(() => {
      if (myChart) {
        renderChart()
      }
    }, 500)
  } catch (error) {
    mapError.value = true
  }
}

/**
 * 根据 GeoJSON 实际 feature name 智能匹配省份名称。
 * DataV GeoJSON 使用全称（如"湖北省"），echarts CDN 可能使用简称（如"湖北"）。
 * 此函数确保无论 GeoJSON 使用哪种格式，都能正确匹配。
 */
function resolveFeatureName(apiName: string): string {
  // 1. 先尝试通过 provinceNameMap 将简称转为全称
  const fullName = provinceNameMap[apiName] || apiName
  if (geoFeatureNames.has(fullName)) return fullName

  // 2. 尝试反向查找：如果 apiName 本身是全称
  if (geoFeatureNames.has(apiName)) return apiName

  // 3. 尝试将全称转为简称
  const shortName = reverseNameMap[apiName] || apiName
  if (geoFeatureNames.has(shortName)) return shortName

  // 4. 兜底：优先返回全称（DataV 格式更常见）
  return fullName
}

function renderChart() {
  if (!myChart) return

  const dataToRender = pendingProvinceData.length > 0 ? pendingProvinceData : props.provinceData

  const colors = ['#E8E8E8', '#FFCCBC', '#FF8A65', '#E64A19', '#BF360C']
  const maxCount = dataToRender.length > 0
    ? Math.max(...dataToRender.map(p => p.count), 1)
    : 1

  const mapData = dataToRender.map(p => {
    const featureName = resolveFeatureName(p.name)
    const ratio = p.count / maxCount
    const colorIndex = Math.min(Math.floor(ratio * (colors.length - 1)), colors.length - 1)
    return {
      name: featureName,
      value: p.count,
      itemStyle: {
        areaColor: colors[colorIndex]
      }
    }
  })

  const isMobile = window.innerWidth < 768

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        if (params.value) {
          return `${params.name}<br/>📸 ${params.value} 张照片`
        }
        return `${params.name}<br/>尚未去过`
      }
    },
    series: [{
      type: 'map',
      map: 'china',
      roam: true,
      selectedMode: false,
      scaleLimit: { min: 0.8, max: 6 },
      label: {
        show: true,
        fontSize: isMobile ? 8 : 10,
        color: '#333'
      },
      itemStyle: {
        borderColor: '#BDBDBD',
        borderWidth: 1,
        areaColor: '#E8E8E8'
      },
      emphasis: {
        label: { color: '#fff', fontSize: 12 },
        itemStyle: {
          areaColor: '#FF6B6B',
          shadowBlur: 10,
          shadowOffsetX: 0,
          shadowColor: 'rgba(0, 0, 0, 0.2)'
        }
      },
      data: mapData
    }]
  }

  myChart.setOption(option, true)
  myChart.resize()

  myChart.off('click')
  myChart.on('click', (params: any) => {
    const shortName = reverseNameMap[params.name] || params.name
    if (shortName === currentActiveProvince.value) {
      currentActiveProvince.value = null
      emit('filterByProvince', null)
    } else if (params.value) {
      currentActiveProvince.value = shortName
      emit('filterByProvince', shortName)
    }
  })
}

function handleResize() {
  myChart?.resize()
}
</script>

<style scoped lang="scss">
.map-section {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  margin: 16px;
  overflow: hidden;
}

.map-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  cursor: pointer;
  user-select: none;

  h3 {
    font-size: 16px;
    margin: 0;
    color: $text-primary;
  }

  .map-header-right {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .map-progress {
    font-size: 13px;
    color: $text-secondary;

    em {
      color: $primary-color;
      font-style: normal;
      font-weight: 600;
    }
  }

  .van-icon {
    color: $text-tertiary;
    font-size: 16px;
  }
}

.map-content {
  padding: 0 16px 16px;
}

.map-loading,
.map-error,
.map-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 300px;
  gap: 12px;
  color: $text-secondary;

  span {
    font-size: 14px;
  }

  p {
    font-size: 12px;
    color: $text-tertiary;
  }
}

.map-error {
  .van-icon {
    color: #ff976a;
  }
}

.map-empty {
  .van-icon {
    color: $primary-light;
  }
}

.china-map.norem {
  width: 100%;
  height: 300px;

  @media (min-width: 768px) {
    height: 400px;
  }

  @media (min-width: 1024px) {
    height: 500px;
  }
}

.map-legend {
  display: flex;
  gap: 16px;
  justify-content: center;
  margin-top: 8px;

  .legend-item {
    font-size: 12px;
    color: $text-tertiary;
    display: flex;
    align-items: center;
    gap: 4px;

    i {
      display: inline-block;
      width: 12px;
      height: 12px;
      border-radius: 2px;
    }
  }
}
</style>
