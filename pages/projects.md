---
title: Projects - Anthony Fu
display: Projects
description: List of projects that I am proud of
wrapperClass: 'text-center'
art: dots
projects:
  Current Focus:
    - name: 'Hmdp'
      link: 'https://github.com/lsyzc/hmdp'
      desc: 'redis 实战项目'
      icon: 'i-simple-icons-redis'
  Finished:
    - name: '苍穹外卖'
      link: 'https://github.com/lsyzc/sky-take-out'
      desc: 'Java 后端基础'
      icon: 'i-devicon:spring saturate-0'
---

<!-- @layout-full-width -->
<ListProjects :projects="frontmatter.projects" />
