package wooteco.subway.domain;

import wooteco.subway.exception.InvalidDistanceException;

public class Distance {

    private static final int MINIMUM_DISTANCE = 1;

    private final int value;

    public Distance(final int value) {
        this.value = value;
        validatePositive(this.value);
    }

    private void validatePositive(final int value) {
        if (value < MINIMUM_DISTANCE) {
            throw new InvalidDistanceException(MINIMUM_DISTANCE);
        }
    }

    public int getValue() {
        return value;
    }
}