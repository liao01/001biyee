# Issue tracker：GitHub

本仓库的任务与规格使用 GitHub Issues 管理，使用 `gh` CLI 操作。仓库由当前 Git remote 自动确定。

## 基本操作

- 创建：`gh issue create --title "..." --body "..."`
- 查看：`gh issue view <number> --comments`
- 列表：`gh issue list`
- 评论：`gh issue comment <number> --body "..."`
- 添加或移除标签：`gh issue edit <number> --add-label "..."` 或 `--remove-label "..."`
- 关闭：`gh issue close <number> --comment "..."`

## Pull requests as a triage surface

**PRs as a request surface: no.**

GitHub 的 Issue 和 PR 共用编号空间。遇到 `#42` 这类引用时，先尝试 `gh pr view 42`，失败后再使用 `gh issue view 42`。

## 技能约定

当技能要求“发布到任务跟踪器”时，创建 GitHub Issue；当技能要求“读取相关 ticket”时，读取对应 Issue 及评论。

## Wayfinding 操作

`wayfinder` 使用一个带有 `wayfinder:map` 标签的 Issue 作为任务地图，并使用子 Issue 表达任务拆分。优先使用 GitHub 原生 Issue dependencies 表达阻塞关系；不可用时，在任务正文顶部使用 `Blocked by: #<number>`。只有全部阻塞项关闭后，任务才视为可领取。
