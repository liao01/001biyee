import { describe, expect, it } from 'vitest'

import { createCommunityMarkerContent } from './communityMarkerContent.js'

describe('社区地图标记内容', () => {
  it('把地点名称作为文本节点而不是 HTML 片段插入', () => {
    const content = createCommunityMarkerContent('<img src=x onerror="window.__xss = true">')

    expect(content.className).toBe('custom-marker')
    expect(content.querySelector('.marker-pin')).not.toBeNull()
    expect(content.querySelector('.marker-label').textContent).toBe('<img src=x onerror="window.__xss = true">')
    expect(content.querySelector('img')).toBeNull()
  })
})
