---
title: Wiki 图片资源
layer: 5
audience: 维护玩家 Wiki 的文档作者
authority: .github/workflows/publish-wiki.yml
---

# Wiki 图片资源

此目录存放玩家 Wiki 使用的图片，按模组版本分目录，文件名描述界面用途。

截图使用无损 WebP，保留界面文字的原始分辨率。

[中文入门页](../wiki/zh-01-getting-started.md)与[英文入门页](../wiki/en-01-getting-started.md)共用图片。Wiki 中的仓库文档使用主仓库的完整 `blob/1.21.1/` 链接；图片链接添加 `?raw=true`，读取图片文件内容：

```text
https://github.com/meteoritel/item-despawn-to-what/blob/1.21.1/docs/wiki-assets/2.0.0/<文件名>.webp?raw=true
```

Wiki 页间导航使用不带扩展名的页面名，例如 `zh-03-conditions`。主仓库的 Markdown 文档链接保留 `.md` 扩展名，例如[文档体系](https://github.com/meteoritel/item-despawn-to-what/blob/1.21.1/docs/README.md)。

Wiki 发布工作流同步 `docs/wiki/` 中平铺的页面；图片由主仓库提供。页面与引用的图片需一同提交到主仓库。
