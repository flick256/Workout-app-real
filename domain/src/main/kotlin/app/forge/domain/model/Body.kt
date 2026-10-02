package app.forge.domain.model

/** Things you can track about your body over time. Weight is in kg, the rest in cm or %. */
enum class BodyMetricKind(val label: String, val unit: String) {
    WEIGHT("Bodyweight", "kg"),
    BODY_FAT("Body fat", "%"),
    WAIST("Waist", "cm"),
    CHEST("Chest", "cm"),
    HIPS("Hips", "cm"),
    ARM("Arm", "cm"),
    THIGH("Thigh", "cm"),
    NECK("Neck", "cm"),
}
