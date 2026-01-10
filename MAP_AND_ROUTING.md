# Campus Mapping and Routing Logic

## Map Rendering
The application uses OpenStreetMap (OSM) tiles that rendered through the OSMDroid library
to display the campus map. Further more the map is restricted to the campus-level zoom for focused navigation, and precise location.

## Location Representation
- Buildings and landmarks are represented as geographic points.
- Walkable paths between locations are represented as line segments.

## Navigation Graph
- Each building acts as a node in the navigation graph.
- Paths act as edges connecting nodes.
- This structure allows efficient path computation within the campus.

## Route Selection
Routes are computed based on connectivity between buildings using predefined paths.
The system selects the shortest available path for pedestrian navigation.

## Distance Calculation
Real-time distance between the user and the route points is calculated using the geographic
coordinates. Thus this distance is used to generate a turn-by-turn navigation instruction.

## Limitations
- Navigation is limited to mapped campus paths.
- Accuracy depends on GPS signal quality.
- Indoor navigation is not supported in the current version.
