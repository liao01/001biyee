# Dify 行程规划工作流接入清单

本文描述旅游平台与现有自托管 Dify 的无秘密契约。Dify 是可替换的建议生成适配器；外挂知识库参与生成，但 MySQL 仍是规划请求、修订建议、用户决定和正式行程的唯一业务事实源。Dify 不得调用正式行程写接口。

## 应用与开始节点

在 Dify 中创建或复用一个 **Workflow** 应用，发布后为旅游平台单独创建 Service API Key。平台只调用 `POST /v1/workflows/run`，并使用 `response_mode=blocking`。

开始节点定义三个字符串变量，名称必须完全一致：

- `planning_request_json`：结构化预算、同行人数、偏好、日期和目的地；
- `itinerary_snapshot_json`：当前行程的版本、日期、目的地、日期项和安排，不含负责人 ID、邮箱、Cookie、访问令牌和安排私人备注；
- `contract_version`：当前固定为 `itinerary-revision/v1`。

`user` 是平台用独立 HMAC Secret 派生的 `travel-...` 伪名，不是 member ID、邮箱或用户名。

## 外挂知识库节点

知识检索节点绑定服务器中已经维护的旅行知识库。检索查询应由结构化目的地、日期和偏好生成，不把整份原始请求或身份信息作为查询。知识库返回内容只进入 LLM 上下文，不直接保存到旅游平台。

输出中的知识来源只允许使用不透明引用 ID，例如 `kb:hangzhou-guide:42`。不得输出原文、长摘要、服务器知识库 ID、内部 URL 或个人信息。引用表示“生成时参考过”，不表示平台已独立核验其事实正确性。

## LLM 输出契约

LLM 节点只输出一个 JSON 对象，不使用 Markdown 代码块，不添加解释文字。根字段为：

```json
{
  "contract_version": "itinerary-revision/v1",
  "summary": "纯文本建议摘要",
  "operations": [],
  "knowledge_reference_ids": []
}
```

允许的操作类型只有 `ADD_ITEM`、`UPDATE_ITEM`、`DELETE_ITEM` 和 `REORDER_DAY_ITEMS`。每项必须有稳定的 ASCII `operation_key` 和纯文本 `summary`。新增/更新项使用 ISO 日期与时间，费用为非负、最多两位小数的当前行程基础币种金额。排序可引用已有 item ID，或通过 `added_by_operation_key` 引用同一建议中更早的新增操作。

输出节点公开：

- `revision_json`：必填，字符串形式的上述严格 JSON；
- `knowledge_reference_ids`：可选字符串数组；若 `revision_json` 已含引用，以其为准；
- `model_name`、`workflow_version`：可选非敏感审计元数据。

平台会在展示前再次执行契约、日期、目标归属、时间冲突、地点、预算、排序完整性和依赖校验。校验失败的结果进入 `INVALID`，不会成为可确认建议。

## 本机配置与秘密

运行时只从被 Git 忽略的本机环境或部署平台 Secret 注入：

- `DIFY_ITINERARY_BASE_URL`
- `DIFY_ITINERARY_API_KEY`
- `DIFY_ITINERARY_USER_HASH_KEY`
- `DIFY_ITINERARY_CONTRACT_VERSION`

不要在仓库、截图、Issue、PR、日志或本文记录实际服务器地址、API Key、知识库 ID、工作流 ID和个人数据。API Key 应只授权这个 Workflow；轮换后立即撤销旧 Key。

## 发布前人工核对

1. 三个开始变量和两个输出字段拼写一致；
2. 知识检索节点绑定正确的旅行知识库，且无身份字段进入检索；
3. LLM 提示明确禁止 Markdown、额外字段、知识正文和正式写入动作；
4. Dify 应用已发布，独立 Service API Key 已注入目标环境；
5. 使用不含真实用户数据的测试请求完成一次 blocking 冒烟；
6. 旅游平台中建议可预览，拒绝不会修改行程，确认必须经过用户操作；
7. 测试数据按批次标识清理并回查。

当前实现不授权 Codex 修改或部署服务器 Dify。在线冒烟需要另行具备有效 Key、目标环境和部署授权；本地及 CI 使用可控 HTTP 替身验证契约。
