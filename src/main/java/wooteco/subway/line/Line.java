package wooteco.subway.line;

import wooteco.subway.station.Station;

import java.util.List;

public class Line {

    private Long id;
    private String name;
    private String color;
    private List<Station> stations;

    public Line() {
    }

    public Line(Long id, String name, String color, List<Station> stations) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.stations = stations;
    }

    public Line(String name, String color, List<Station> stations) {
        this.name = name;
        this.color = color;
        this.stations = stations;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    public List<Station> getStations() {
        return stations;
    }
}
