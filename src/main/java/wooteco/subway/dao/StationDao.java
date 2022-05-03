package wooteco.subway.dao;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.util.ReflectionUtils;
import wooteco.subway.domain.Station;

@Repository
public class StationDao {
    private static Long seq = 0L;
    private static List<Station> stations = new ArrayList<>();

    public Station save(Station station) {
        Station persistStation = createNewObject(station);
        stations.add(persistStation);
        return persistStation;
    }

    public List<Station> findAll() {
        return stations;
    }

    public Optional<Station> findByName(String name) {
        return stations.stream()
                .filter(station -> station.getName().equals(name))
                .findAny();
    }

    private Station createNewObject(Station station) {
        Field field = ReflectionUtils.findField(Station.class, "id");
        field.setAccessible(true);
        ReflectionUtils.setField(field, station, ++seq);
        return station;
    }

    public void deleteById(Long id) {
        Optional<Station> station = stations.stream()
                .filter(s -> s.matchId(id))
                .findFirst();

        station.ifPresent(value -> stations.remove(value));
    }

    public void deleteAll() {
        stations = new ArrayList<>();
    }
}
