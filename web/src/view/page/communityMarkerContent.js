export const createCommunityMarkerContent = (name) => {
  const content = document.createElement('div')
  content.className = 'custom-marker'

  const pin = document.createElement('div')
  pin.className = 'marker-pin'
  content.append(pin)

  const label = document.createElement('div')
  label.className = 'marker-label'
  label.textContent = typeof name === 'string' ? name : ''
  content.append(label)

  return content
}
