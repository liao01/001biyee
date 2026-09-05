import ItineraryList from './ItineraryList.vue'
import ItineraryCreate from './ItineraryCreate.vue'
import ItineraryEditor from './ItineraryEditor.vue'

export const itineraryRoutes = [
  { path: 'itineraries', name: 'itineraries', component: ItineraryList, meta: { requiresAuth: true } },
  { path: 'itineraries/new', name: 'itinerary-create', component: ItineraryCreate, meta: { requiresAuth: true } },
  { path: 'itineraries/:itineraryId', name: 'itinerary-editor', component: ItineraryEditor, meta: { requiresAuth: true } },
]
