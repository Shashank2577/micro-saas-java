sed -i 's/private boolean isActive = true;/private boolean isActive;/' apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/model/OnboardingTemplate.java
sed -i 's/private boolean isActive;/@Builder.Default\n    private boolean isActive = true;/' apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/model/OnboardingTemplate.java

sed -i 's/private boolean isRequired = true;/private boolean isRequired;/' apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/model/TemplateTask.java
sed -i 's/private boolean isRequired;/@Builder.Default\n    private boolean isRequired = true;/' apps/06-employee-onboarding-orchestrator/src/main/java/com/changelog/model/TemplateTask.java
