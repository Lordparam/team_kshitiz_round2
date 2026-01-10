# Testing and Evaluation

## Testing Scenarios
- Outdoor building recognition under daylight conditions
- Indoor usage in classrooms and corridors
- GPS-based location detection near campus buildings

## Observations
- The AI model performs well for outdoor building exteriors
- Indoor scenes may result in low-confidence or incorrect predictions
- GPS accuracy varies slightly depending on surrounding structures

## Identified Limitations
- Indoor environments are not supported in the current version
- Model accuracy depends on lighting and camera angle
- GPS drift may occur in dense campus areas

## Suggestions for Improvement
- Introduce an explicit Indoor/Other classification class
- Apply stricter confidence thresholds for predictions
- Improve user guidance and warnings for unsupported scenarios
