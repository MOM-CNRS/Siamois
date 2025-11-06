package fr.siamois.domain.models.misc;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class ProgressWrapper {

    private int currentStepNumber = 0;
    private int totalSteps = 0;

    public void incrementStep() {
        if (currentStepNumber < totalSteps) {
            currentStepNumber++;
        }
    }

    public void incrementStep(int steps) {
        currentStepNumber += steps;
        if (currentStepNumber > totalSteps) {
            currentStepNumber = totalSteps;
        }
    }

    public void reset() {
        currentStepNumber = 0;
        totalSteps = 0;
    }

    public Integer getProgressPercentage() {
        if (totalSteps == 0) {
            return 0;
        }
        return (currentStepNumber * 100) / totalSteps;
    }

    public boolean isStarted() {
        return currentStepNumber > 0;
    }

}
