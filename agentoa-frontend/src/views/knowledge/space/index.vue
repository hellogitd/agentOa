<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"><span class="card-header-label">知识空间</span></el-col>
          <el-col v-hasPermi="['kn:space:add']" :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd()">新建空间</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-input v-model="query.keyword" placeholder="空间名称" clearable prefix-icon="Search" class="w-180px" @keyup.enter="getList" />
          </el-col>
          <el-col :span="1.5">
            <el-select v-model="query.spaceType" placeholder="空间类型" clearable class="w-120px" @change="getList">
              <el-option v-for="item in kn_space_type" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-col>
          <el-col :span="1.5">
            <el-button icon="Refresh" @click="getList">刷新</el-button>
          </el-col>
        </el-row>
      </template>

      <el-table v-loading="loading" :data="list" border>
        <el-table-column label="空间名称" align="left" prop="name" min-width="160" />
        <el-table-column label="类型" align="center" width="90">
          <template #default="scope">
            <dict-tag :options="kn_space_type" :value="scope.row.spaceType" />
          </template>
        </el-table-column>
        <el-table-column label="我的角色" align="center" width="110">
          <template #default="scope">
            <dict-tag v-if="scope.row.myRole" :options="kn_space_role" :value="scope.row.myRole" />
            <el-tag v-else type="info">无权限</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="成员数" align="center" prop="memberCount" width="80" />
        <el-table-column label="创建人" align="center" prop="createByName" width="110" />
        <el-table-column label="创建时间" align="center" prop="createTime" width="170" />
        <el-table-column label="简介" align="left" prop="description" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" align="center" width="220" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button link type="primary" icon="User" @click="handleMembers(scope.row)">成员</el-button>
            <el-button
              v-if="scope.row.myRole === 'OWNER'"
              v-hasPermi="['kn:space:edit']"
              link
              type="primary"
              icon="Edit"
              @click="handleEdit(scope.row)"
              >修改</el-button
            >
            <el-button
              v-if="scope.row.myRole === 'OWNER'"
              v-hasPermi="['kn:space:remove']"
              link
              type="danger"
              icon="Delete"
              @click="handleDelete(scope.row)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <el-dialog :title="dialog.title" v-model="dialog.visible" width="560px" append-to-body>
      <el-form ref="spaceFormRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="空间名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入空间名称" maxlength="128" />
        </el-form-item>
        <el-form-item label="空间类型" prop="spaceType">
          <el-radio-group v-model="form.spaceType">
            <el-radio v-for="item in kn_space_type" :key="item.value" :value="item.value">{{ item.label }}</el-radio>
          </el-radio-group>
          <div class="text-gray-400 text-xs">公开全员可见；私密仅创建人；团队指定成员可见</div>
        </el-form-item>
        <el-form-item label="空间简介">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="dialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer v-model="memberVisible" :title="`空间成员 - ${currentSpace?.name ?? ''}`" size="640px">
      <el-row :gutter="10" class="mb8">
        <el-col v-hasPermi="['kn:member:add']" :span="1.5">
          <el-button type="primary" plain icon="Plus" :disabled="currentSpace?.myRole !== 'OWNER'" @click="handleAddMember">添加成员</el-button>
        </el-col>
      </el-row>
      <el-table v-loading="memberLoading" :data="members" border>
        <el-table-column label="成员" align="center" prop="nickname" min-width="120" />
        <el-table-column label="角色" align="center" width="120">
          <template #default="scope">
            <dict-tag :options="kn_space_role" :value="scope.row.role" />
          </template>
        </el-table-column>
        <el-table-column label="授权人" align="center" prop="grantedByName" width="110" />
        <el-table-column label="授权时间" align="center" prop="grantedTime" width="170" />
        <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button
              v-hasPermi="['kn:member:edit']"
              link
              type="primary"
              icon="Edit"
              :disabled="currentSpace?.myRole !== 'OWNER'"
              @click="handleEditMember(scope.row)"
              >改角色</el-button
            >
            <el-button
              v-hasPermi="['kn:member:remove']"
              link
              type="danger"
              icon="Delete"
              :disabled="currentSpace?.myRole !== 'OWNER'"
              @click="handleRemoveMember(scope.row)"
              >移除</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <el-dialog :title="memberDialog.title" v-model="memberDialog.visible" width="480px" append-to-body>
      <el-form ref="memberFormRef" :model="memberForm" :rules="memberRules" label-width="90px">
        <el-form-item label="账号" prop="userId">
          <el-select v-model="memberForm.userId" :disabled="!!memberDialog.userId" filterable placeholder="选择账号" class="w-full">
            <el-option v-for="u in userOptions" :key="u.userId" :label="u.nickName" :value="String(u.userId)" />
          </el-select>
        </el-form-item>
        <el-form-item label="空间角色" prop="role">
          <el-select v-model="memberForm.role" placeholder="选择角色" class="w-full">
            <el-option v-for="item in kn_space_role" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <div class="text-gray-400 text-xs">拥有者可管理；编辑者可编辑；评论者/查看者只读（评论为 P1）</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitMemberForm">确 定</el-button>
          <el-button @click="memberDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="KbSpace" lang="ts">
import {
  listSpace,
  addSpace,
  updateSpace,
  delSpace,
  listMember,
  addMember,
  updateMember,
  delMember,
  SpaceVO,
  SpaceForm,
  MemberVO,
  MemberForm
} from '@/api/knowledge';
import { listUser } from '@/api/system/user';
import { UserVO } from '@/api/system/user/types';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { kn_space_type, kn_space_role } = toRefs<any>(proxy?.useDict('kn_space_type', 'kn_space_role'));

const loading = ref(true);
const list = ref<SpaceVO[]>([]);
const total = ref(0);
const query = ref<any>({ pageNum: 1, pageSize: 10, keyword: undefined, spaceType: undefined });
const dialog = reactive<any>({ visible: false, title: '', id: undefined as string | undefined });
const formRef = ref();
const form = ref<SpaceForm>({ name: '', spaceType: '3', description: '', remark: '' });
const rules = {
  name: [{ required: true, message: '请输入空间名称', trigger: 'blur' }],
  spaceType: [{ required: true, message: '请选择空间类型', trigger: 'change' }]
};

const memberVisible = ref(false);
const memberLoading = ref(false);
const currentSpace = ref<SpaceVO>();
const members = ref<MemberVO[]>([]);
const memberDialog = reactive<any>({ visible: false, title: '', userId: undefined as string | undefined });
const memberFormRef = ref();
const memberForm = ref<MemberForm>({ userId: '', role: 'VIEWER' });
const memberRules = {
  userId: [{ required: true, message: '请选择账号', trigger: 'change' }],
  role: [{ required: true, message: '请选择空间角色', trigger: 'change' }]
};
const userOptions = ref<UserVO[]>([]);

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listSpace(query.value);
    list.value = res.data?.records ?? [];
    total.value = res.data?.total ?? 0;
  } finally {
    loading.value = false;
  }
};

const loadUsers = async () => {
  if (userOptions.value.length > 0) return;
  try {
    const res: any = await listUser({ pageNum: 1, pageSize: 200 } as any);
    userOptions.value = res.rows ?? res.data?.rows ?? [];
  } catch {
    userOptions.value = [];
  }
};

const handleAdd = () => {
  form.value = { name: '', spaceType: '3', description: '', remark: '' };
  dialog.title = '新建知识空间';
  dialog.id = undefined;
  dialog.visible = true;
};

const handleEdit = (row: SpaceVO) => {
  form.value = { name: row.name, spaceType: String(row.spaceType), description: row.description, remark: row.remark };
  dialog.title = '修改知识空间';
  dialog.id = row.id;
  dialog.visible = true;
};

const submitForm = async () => {
  const valid = await (proxy?.$refs['spaceFormRef'] as any)?.validate().catch(() => false);
  if (!valid) return;
  if (dialog.id) {
    await updateSpace(dialog.id, form.value);
    proxy?.$modal.msgSuccess('修改成功');
  } else {
    await addSpace(form.value);
    proxy?.$modal.msgSuccess('创建成功');
  }
  dialog.visible = false;
  await getList();
};

const handleDelete = async (row: SpaceVO) => {
  await proxy?.$modal.confirm(`确认删除空间「${row.name}」？需先清空文档与文件`);
  await delSpace(row.id);
  proxy?.$modal.msgSuccess('删除成功');
  await getList();
};

const handleMembers = async (row: SpaceVO) => {
  currentSpace.value = row;
  memberVisible.value = true;
  await loadMemberList();
};

const loadMemberList = async () => {
  if (!currentSpace.value) return;
  memberLoading.value = true;
  try {
    const res: any = await listMember(currentSpace.value.id);
    members.value = res.data ?? [];
  } finally {
    memberLoading.value = false;
  }
};

const handleAddMember = async () => {
  await loadUsers();
  memberForm.value = { userId: '', role: 'VIEWER' };
  memberDialog.title = '添加成员';
  memberDialog.userId = undefined;
  memberDialog.visible = true;
};

const handleEditMember = (row: MemberVO) => {
  memberForm.value = { userId: row.userId, role: row.role };
  memberDialog.title = '修改成员角色';
  memberDialog.userId = row.userId;
  memberDialog.visible = true;
};

const submitMemberForm = async () => {
  const valid = await (proxy?.$refs['memberFormRef'] as any)?.validate().catch(() => false);
  if (!valid || !currentSpace.value) return;
  if (memberDialog.userId) {
    await updateMember(currentSpace.value.id, memberDialog.userId, memberForm.value);
    proxy?.$modal.msgSuccess('修改成功');
  } else {
    await addMember(currentSpace.value.id, memberForm.value);
    proxy?.$modal.msgSuccess('添加成功');
  }
  memberDialog.visible = false;
  await loadMemberList();
};

const handleRemoveMember = async (row: MemberVO) => {
  if (!currentSpace.value) return;
  await proxy?.$modal.confirm(`确认移除成员「${row.nickname ?? row.userId}」？移除后旧链接立即失效`);
  await delMember(currentSpace.value.id, row.userId);
  proxy?.$modal.msgSuccess('移除成功');
  await loadMemberList();
};

onMounted(() => {
  getList();
});
</script>
