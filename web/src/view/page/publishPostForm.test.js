import { describe, expect, it } from 'vitest'

import { buildPostPayload, validatePostDraft } from './publishPostForm.js'

describe('发布表单边界', () => {
  it('要求标题、正文、正式分类和至少一张图片', () => {
    expect(validatePostDraft({ title: '', content: '', categoryCode: '', images: [] }))
      .toBe('请填写标题')
    expect(validatePostDraft({ title: '标题', content: '正文', categoryCode: '', images: [{}] }))
      .toBe('请选择内容分类')
    expect(validatePostDraft({ title: '标题', content: '正文', categoryCode: 'FOOD', images: [] }))
      .toBe('请至少添加一张旅行图片')
    expect(validatePostDraft({ title: '标题', content: '正文', categoryCode: 'FOOD', images: [{}] }))
      .toBe('')
  })

  it('只提交帖子契约需要的字段，不伪造用户、位置或状态', () => {
    expect(buildPostPayload({
      title: ' 海边散步 ',
      content: ' 正文 ',
      categoryCode: 'CITY_WALK',
      images: [{ imageUrl: 'data:image/png;base64,x', seq: 1, description: '' }],
    })).toEqual({
      title: '海边散步',
      content: '正文',
      categoryCode: 'CITY_WALK',
      images: [{ imageUrl: 'data:image/png;base64,x', seq: 1, description: '' }],
    })
  })
})
