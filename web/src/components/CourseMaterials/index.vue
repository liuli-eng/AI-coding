<script setup lang="ts">
import type { MaterialSummary } from '@/api/learning';
import { useUserStore } from '@/stores/modules/user';
import { saveAttachment } from '@/utils/download-attachment';

const props = defineProps<{
  materials: MaterialSummary[];
  scopeKey: string;
  download: (materialId: string, signal: AbortSignal) => Promise<Blob>;
}>();
const user = useUserStore();
const pending = ref<string[]>([]);
const error = ref('');
const controllers = new Set<AbortController>();
let sequence = 0;
function reset() {
  sequence++;
  controllers.forEach(controller => controller.abort());
  controllers.clear();
  pending.value = [];
  error.value = '';
}
watch([() => props.scopeKey, () => props.materials, () => user.token], reset, { flush: 'sync' });
onBeforeUnmount(reset);
function sizeLabel(size: number) {
  return size >= 1024 * 1024 ? `${(size / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.ceil(size / 1024))} KB`;
}
async function download(material: MaterialSummary) {
  if (pending.value.includes(material.id))
    return;
  const current = sequence;
  const controller = new AbortController();
  controllers.add(controller);
  pending.value.push(material.id);
  error.value = '';
  try {
    const blob = await props.download(material.id, controller.signal);
    if (current === sequence && !controller.signal.aborted)
      saveAttachment(blob, material.name);
  }
  catch (cause) {
    if (current === sequence && !controller.signal.aborted)
      error.value = cause instanceof Error ? cause.message : '下载失败，请重试。';
  }
  finally {
    controllers.delete(controller);
    if (current === sequence)
      pending.value = pending.value.filter(id => id !== material.id);
  }
}
</script>

<template>
  <section v-if="materials.length" class="course-materials" aria-label="课程资料">
    <h3>课程资料</h3>
    <ul>
      <li v-for="material in materials" :key="material.id">
        <div class="material-copy">
          <span class="material-name">{{ material.name }}</span>
          <span class="material-size">{{ sizeLabel(material.size) }}</span>
        </div>
        <el-button :loading="pending.includes(material.id)" :aria-label="`下载 ${material.name}`" @click="download(material)">
          下载
        </el-button>
      </li>
    </ul>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
  </section>
</template>

<style scoped>
.course-materials { margin: 16px 0; min-width: 0; }
h3 { margin: 0 0 8px; font-size: 15px; font-weight: 600; color: var(--el-text-color-primary); }
ul { list-style: none; margin: 0; padding: 0; max-height: 200px; overflow-y: auto; }
li { display: flex; align-items: center; gap: 12px; justify-content: space-between; padding: 8px 0; }
.material-copy { min-width: 0; }
.material-name { display: block; color: var(--el-text-color-primary); overflow-wrap: anywhere; font-size: 14px; line-height: 1.5; }
.material-size { color: var(--el-text-color-regular); font-size: 13px; }
.el-button { flex-shrink: 0; }
</style>
