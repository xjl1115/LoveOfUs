<template>
  <div class="address-page">
    <van-nav-bar :title="selectMode ? '选择收货地址' : '我的地址'" left-arrow fixed placeholder @click-left="onBack" />

    <div class="address-content">
      <div v-if="loading" class="loading-block"><van-loading size="24" /></div>

      <template v-else>
        <div v-if="addresses.length === 0" class="empty-block">
          <div class="empty-icon">📍</div>
          <div class="empty-text">还没有收货地址</div>
          <van-button size="small" type="primary" round @click="openCreate">新增收货地址</van-button>
        </div>

        <div v-else class="address-list">
          <div
            v-for="a in addresses"
            :key="a.id"
            class="address-card"
            :class="{ active: selectMode && selectedId === a.id }"
            @click="onSelect(a)"
          >
            <div class="address-top">
              <div class="address-receiver">
                <span class="receiver-name">{{ a.receiverName }}</span>
                <span class="receiver-phone">{{ a.receiverPhone }}</span>
                <van-tag v-if="a.isDefault === 1" type="danger" plain class="default-tag">默认</van-tag>
              </div>
              <div v-if="selectMode" class="radio-box">
                <van-icon :name="selectedId === a.id ? 'checked' : 'circle'" :color="selectedId === a.id ? '#e8437f' : '#c8c9cc'" />
              </div>
            </div>
            <div class="address-detail">{{ a.province }}{{ a.city }}{{ a.detailAddress }}</div>
            <div class="address-actions" v-if="!selectMode" @click.stop>
              <span v-if="a.isDefault !== 1" class="action-link" @click="makeDefault(a)">设为默认</span>
              <span class="action-link" @click="openEdit(a)">编辑</span>
              <span class="action-link danger" @click="remove(a)">删除</span>
            </div>
          </div>
        </div>
      </template>
    </div>

    <div class="footer-bar">
      <template v-if="!selectMode">
        <van-button block type="primary" round icon="plus" @click="openCreate">新增收货地址</van-button>
      </template>
      <template v-else>
        <van-button block type="primary" round :disabled="!selectedId" @click="confirmSelect">确认收货地址</van-button>
      </template>
    </div>

    <!-- 新增 / 编辑地址弹窗 -->
    <van-popup v-model:show="showForm" round position="bottom" :style="{ height: '85%' }">
      <div class="form-popup">
        <div class="popup-header">
          <h3>{{ editingId ? '编辑收货地址' : '新增收货地址' }}</h3>
        </div>
        <van-cell-group inset>
          <van-field v-model="form.receiverName" label="收货人" placeholder="请输入收货人姓名" maxlength="20" />
          <van-field v-model="form.receiverPhone" label="手机号" type="tel" placeholder="请输入收货人手机号" maxlength="11" />
          <van-field
            v-model="regionText"
            label="所在地区"
            placeholder="请选择省市区"
            is-link
            readonly
            @click="showRegionPicker = true"
          />
          <van-field
            v-model="form.detailAddress"
            label="详细地址"
            type="textarea"
            rows="2"
            autosize
            maxlength="120"
            show-word-limit
            placeholder="街道、门牌号等"
          />
          <van-cell center title="设为默认地址">
            <template #right-icon>
              <van-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" size="20" />
            </template>
          </van-cell>
        </van-cell-group>
        <div class="form-actions">
          <van-button block round type="primary" :loading="submitting" @click="submit">保存</van-button>
        </div>
      </div>
    </van-popup>

    <!-- 省市区选择器 -->
    <van-popup v-model:show="showRegionPicker" round position="bottom">
      <van-cascader
        title="请选择所在地区"
        :options="regionColumns"
        @close="showRegionPicker = false"
        @finish="onRegionFinish"
      />
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { provinces } from '@/data/regions'
import {
  listAddresses,
  createAddress,
  updateAddress,
  deleteAddress,
  setDefaultAddress,
  type ShippingAddress
} from '@/api/address'

const route = useRoute()
const router = useRouter()

const addresses = ref<ShippingAddress[]>([])
const loading = ref(false)
const selectedId = ref<number | null>(null)

const selectMode = computed(() => route.query.select === '1')

// 表单
const showForm = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const form = reactive({
  receiverName: '',
  receiverPhone: '',
  province: '',
  city: '',
  detailAddress: '',
  isDefault: 0
})

const showRegionPicker = ref(false)
const regionColumns = provinces.map(p => ({
  text: p.name,
  value: p.name,
  children: p.cities.map(c => ({ text: c.name, value: c.name }))
}))

const regionText = computed(() => (form.province && form.city ? `${form.province} ${form.city}` : ''))

async function load() {
  loading.value = true
  try {
    addresses.value = (await listAddresses()) || []
    // 默认地址预选中
    if (selectMode.value) {
      const def = addresses.value.find(a => a.isDefault === 1)
      selectedId.value = def ? def.id : (addresses.value[0]?.id ?? null)
    }
  } catch (e: any) {
    showToast(e?.message || '加载地址失败')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.receiverName = ''
  form.receiverPhone = ''
  form.province = ''
  form.city = ''
  form.detailAddress = ''
  form.isDefault = 0
}

function openCreate() {
  resetForm()
  editingId.value = null
  showForm.value = true
}

function openEdit(a: ShippingAddress) {
  form.receiverName = a.receiverName
  form.receiverPhone = a.receiverPhone
  form.province = a.province
  form.city = a.city
  form.detailAddress = a.detailAddress
  form.isDefault = a.isDefault
  editingId.value = a.id
  showForm.value = true
}

function onRegionFinish({ selectedOptions }: any) {
  form.province = selectedOptions[0]?.text || ''
  form.city = selectedOptions[1]?.text || ''
  showRegionPicker.value = false
}

function validate(): string | null {
  if (!form.receiverName.trim()) return '请填写收货人姓名'
  if (!/^1[3-9]\d{9}$/.test(form.receiverPhone.trim())) return '请填写正确的手机号'
  if (!form.province || !form.city) return '请选择所在地区'
  if (!form.detailAddress.trim()) return '请填写详细地址'
  return null
}

async function submit() {
  const err = validate()
  if (err) {
    showToast(err)
    return
  }
  submitting.value = true
  const payload = {
    receiverName: form.receiverName.trim(),
    receiverPhone: form.receiverPhone.trim(),
    province: form.province,
    city: form.city,
    detailAddress: form.detailAddress.trim(),
    isDefault: form.isDefault
  }
  try {
    if (editingId.value) {
      await updateAddress(editingId.value, payload)
      showToast('修改成功')
    } else {
      await createAddress(payload)
      showToast('保存成功')
    }
    showForm.value = false
    await load()
    // 选择模式下，新增后自动选中刚保存的地址
    if (selectMode.value && !editingId.value && addresses.value.length > 0) {
      const saved = addresses.value.find(a =>
        a.receiverName === payload.receiverName &&
        a.receiverPhone === payload.receiverPhone &&
        a.detailAddress === payload.detailAddress
      )
      if (saved) selectedId.value = saved.id
    }
  } catch (e: any) {
    showToast(e?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

async function remove(a: ShippingAddress) {
  try {
    await showConfirmDialog({ title: '删除地址', message: '确定删除该收货地址吗？' })
  } catch {
    return
  }
  try {
    await deleteAddress(a.id)
    showToast('已删除')
    await load()
  } catch (e: any) {
    showToast(e?.message || '删除失败')
  }
}

async function makeDefault(a: ShippingAddress) {
  try {
    await setDefaultAddress(a.id)
    await load()
  } catch (e: any) {
    showToast(e?.message || '设置失败')
  }
}

function onSelect(a: ShippingAddress) {
  if (selectMode.value) {
    selectedId.value = a.id
  }
}

function confirmSelect() {
  if (!selectedId.value) {
    showToast('请选择收货地址')
    return
  }
  const productId = route.query.productId as string
  const quantity = route.query.quantity as string
  router.replace({
    path: '/points-shop/order',
    query: { productId, quantity, addressId: String(selectedId.value) }
  })
}

function onBack() {
  if (window.history.length > 1) router.back()
  else router.push('/profile')
}

onMounted(load)
</script>

<style scoped lang="scss">
.address-page {
  min-height: 100vh;
  background: $bg-color;
  padding-bottom: 80px;
}

.address-content {
  padding: 12px 16px;
}

.loading-block {
  display: flex;
  justify-content: center;
  padding: 40px 0;
}

.empty-block {
  text-align: center;
  padding: 60px 0;
  .empty-icon {
    font-size: 44px;
    margin-bottom: 12px;
  }
  .empty-text {
    color: $text-secondary;
    font-size: 14px;
    margin-bottom: 16px;
  }
}

.address-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.address-card {
  background: #fff;
  border-radius: 12px;
  padding: 14px 16px;
  transition: border-color 0.2s;

  &.active {
    border: 1px solid #e8437f;
  }

  .address-top {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .address-receiver {
    display: flex;
    align-items: center;
    gap: 8px;
    .receiver-name {
      font-size: 15px;
      font-weight: 600;
      color: $text-primary;
    }
    .receiver-phone {
      font-size: 13px;
      color: $text-secondary;
    }
    .default-tag {
      margin-left: 4px;
    }
  }

  .radio-box {
    font-size: 20px;
  }

  .address-detail {
    font-size: 13px;
    color: $text-secondary;
    margin-top: 8px;
    line-height: 1.5;
  }

  .address-actions {
    display: flex;
    justify-content: flex-end;
    gap: 16px;
    margin-top: 12px;
    .action-link {
      font-size: 13px;
      color: #1989fa;
      &.danger {
        color: #ee0a24;
      }
    }
  }
}

.footer-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
  background: #fff;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.04);
}

.form-popup {
  padding: 16px;
  .popup-header h3 {
    margin: 0 0 16px;
    font-size: 16px;
    font-weight: 600;
  }
  .form-actions {
    margin-top: 24px;
  }
}
</style>