<script setup lang="ts">
import type { Photo } from '../../../photos/data'
import raw from '../../../photos/data'

const photos: Photo[] = [
  'p-2025-09-02-13-07-47-000-1',
  'p-2025-09-02-13-05-34-000-1',
  'image',
].map(name => raw.find(p => p.url.includes(name))!)
</script>
<!-- 上面的数组中如果是不能访问到的图像，build 时会出问题 -->

<template>
  <PhotoGrid :photos="photos" />
</template>
