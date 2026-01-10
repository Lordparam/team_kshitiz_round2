# Testing and Evaluation

## Testing Scenarios
- Outdoor building recognition under daylight conditions as well as in dim conditions
- Indoor usage in classrooms and corridors so as to eliminate any edge cases if exists.
- GPS-based location detection near campus buildings to chekc the accuracy of the model.

## Observations
- The AI model performs well for outdoor building exteriors, and works for indoor rooms to identify interiors.
- Indoor scenes may result in low-confidence or incorrect predictions, but most of them they will be labelled as 'Indoor' only.
- GPS accuracy varies slightly depending on surrounding structures, and might crash if you are not outside.

## Identified Limitations
- Indoor environments are not supported in the current version, as GPS tracking is not possible there.
- Model accuracy depends on lighting and camera angle, due to resource decifiency.
- GPS drift might occur in the dense campus areas, or in any low signal spots.

## Suggestions for Improvement
- Introduce an explicit Indoor/Other classification class.
- Apply stricter confidence thresholds for predictions, so as to make it more reliable.
- Improve user guidance and warnings for unsupported scenarios, so as to improve comaptibility and confidence score of user too.
