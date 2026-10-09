<template>
  <el-dialog
    :title="`成员管理 - ${kb?.name ?? ''}`"
    :model-value="visible"
    width="520px"
    append-to-body
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form inline>
      <el-form-item label="账号 ID">
        <el-input v-model="newMemberId" placeholder="成员账号 ID" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleAddMember">添加</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="members" border>
      <el-table-column label="账号 ID" align="center" prop="userId" width="140" />
      <el-table-column label="账号" align="center" prop="userName" />
      <el-table-column label="昵称" align="center" prop="nickName" />
      <el-table-column label="操作" align="center" width="90">
        <template #default="scope">
          <el-button link type="danger" icon="Delete" @click="handleRemoveMember(scope.row)">移除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>
</template>

<script setup name="KbMemberDialog" lang="ts">
import type { PropType } from 'vue';
import { listKbMembers, addKbMembers, deleteKbMember, type KbVO, type KbMemberVO } from '@/api/ai';

const props = defineProps({
  visible: { type: Boolean, default: false },
  kb: { type: Object as PropType<KbVO | null>, default: null }
});
const emit = defineEmits(['update:visible', 'changed']);

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const members = ref<KbMemberVO[]>([]);
const newMemberId = ref('');

const loadMembers = async () => {
  if (!props.kb) return;
  const res: any = await listKbMembers(props.kb.id);
  members.value = res.data ?? [];
};

watch(
  () => props.visible,
  async (open) => {
    if (open) {
      newMemberId.value = '';
      await loadMembers();
    }
  }
);

const handleAddMember = async () => {
  if (!props.kb || !newMemberId.value.trim()) return;
  await addKbMembers(props.kb.id, [newMemberId.value.trim()]);
  proxy?.$modal.msgSuccess('已添加');
  newMemberId.value = '';
  await loadMembers();
  emit('changed');
};

const handleRemoveMember = async (row: KbMemberVO) => {
  if (!props.kb) return;
  await deleteKbMember(props.kb.id, row.userId);
  proxy?.$modal.msgSuccess('已移除');
  await loadMembers();
  emit('changed');
};
</script>
