<template>
  <el-drawer :title="name + ' - 员工详情'" :model-value="visible" size="560px" @update:model-value="emit('update:visible', $event)">
    <el-descriptions :column="2" border>
      <el-descriptions-item label="工号">{{ data?.employeeNo }}</el-descriptions-item>
      <el-descriptions-item label="姓名">{{ data?.name }}</el-descriptions-item>
      <el-descriptions-item label="性别">{{ genderLabel(data?.gender) }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <dict-tag :options="hr_employee_status" :value="data?.status" />
      </el-descriptions-item>
      <el-descriptions-item label="部门">{{ data?.deptName }}</el-descriptions-item>
      <el-descriptions-item label="岗位">{{ data?.postName }}</el-descriptions-item>
      <el-descriptions-item label="职级">{{ data?.positionLevel }}</el-descriptions-item>
      <el-descriptions-item label="手机号">{{ data?.phone }}</el-descriptions-item>
      <el-descriptions-item label="邮箱" :span="2">{{ data?.email }}</el-descriptions-item>
      <el-descriptions-item label="身份证号">{{ data?.idCardNo }}</el-descriptions-item>
      <el-descriptions-item label="入职日期">{{ data?.entryDate }}</el-descriptions-item>
      <el-descriptions-item label="转正日期">{{ data?.regularDate }}</el-descriptions-item>
      <el-descriptions-item label="离职日期">{{ data?.leaveDate }}</el-descriptions-item>
      <el-descriptions-item label="备注" :span="2">{{ data?.remark }}</el-descriptions-item>
    </el-descriptions>

    <el-divider content-position="left">变动历史</el-divider>
    <el-timeline v-if="changes.length">
      <el-timeline-item v-for="item in changes" :key="item.id" :timestamp="item.operateTime">
        <div>{{ item.detail }}（{{ item.fromStatus || '-' }} → {{ item.toStatus || '-' }}）</div>
        <div class="text-xs text-gray-400">操作人：{{ item.operatorName || item.eventType }}</div>
      </el-timeline-item>
    </el-timeline>
    <el-empty v-else description="暂无变动记录" :image-size="80" />
  </el-drawer>
</template>

<script setup name="EmployeeDetailDrawer" lang="ts">
import type { PropType } from 'vue';
import { EmployeeChangeVO, EmployeeVO } from '@/api/hr';

defineProps({
  visible: { type: Boolean, default: false },
  name: { type: String, default: '' },
  data: { type: Object as PropType<EmployeeVO | null>, default: null },
  changes: { type: Array as PropType<EmployeeChangeVO[]>, default: () => [] }
});
const emit = defineEmits(['update:visible']);

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { hr_employee_status, sys_user_sex } = toRefs<any>(proxy?.useDict('hr_employee_status', 'sys_user_sex'));

const genderLabel = (value?: string) => sys_user_sex.value?.find((d: any) => d.value === value)?.label ?? '-';
</script>
