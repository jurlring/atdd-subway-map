package wooteco.subway.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.restassured.RestAssured;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import wooteco.subway.domain.Line;
import wooteco.subway.domain.Section;
import wooteco.subway.domain.Station;
import wooteco.subway.dto.LineRequest;
import wooteco.subway.dto.LineResponse;
import wooteco.subway.dto.SectionRequest;
import wooteco.subway.dto.StationResponse;

@DisplayName("지하철 노선 관련 기능")
public class LineAcceptanceTest extends AcceptanceTest {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @DisplayName("line 을 저장한다.")
    @Test
    void saveLine() {
        // given
        Long stationId1 = insertStationData("강남역");
        Long stationId2 = insertStationData("선릉역");
        LineRequest request = new LineRequest("2호선", "green", stationId1, stationId2, 10);

        // when
        ExtractableResponse<Response> response = executePostLineApi(request, "/lines");

        List<Station> expectStations = response.body().jsonPath().getList("stations", StationResponse.class)
                .stream().map(it -> new Station(it.getId(), it.getName()))
                .collect(Collectors.toList());

        // then
        assertAll(() -> assertThat(response.statusCode()).isEqualTo(HttpStatus.CREATED.value()),
                () -> assertThat(response.header("Location")).isNotBlank(),
                () -> assertThat(expectStations).isEqualTo(
                        List.of(new Station(stationId1, "강남역"), new Station(stationId2, "선릉역"))));
    }

    @DisplayName("empty name 을 이용하여 line 을 저장할 경우, 예외를 발생시킨다.")
    @Test
    void saveLineExceptionEmptyName() {
        //given
        Long stationId1 = insertStationData("강남역");
        Long stationId2 = insertStationData("선릉역");
        LineRequest request = new LineRequest("", "green", stationId1, stationId2, 10);

        // when
        ExtractableResponse<Response> response = executePostLineApi(request, "/lines");

        //then
        assertAll(() -> assertThat(response.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value()),
                () -> assertThat(response.body().asString()).isEqualTo("line의 name(은)는 공백일 수 없습니다 입력된 값: []"));
    }

    @DisplayName("empty color 을 이용하여 line 을 저장할 경우, 예외를 발생시킨다.")
    @Test
    void saveLineExceptionEmptyColor() {
        //given
        Long stationId1 = insertStationData("강남역");
        Long stationId2 = insertStationData("선릉역");
        LineRequest request = new LineRequest("2호선", "", stationId1, stationId2, 10);

        // when
        ExtractableResponse<Response> response = executePostLineApi(request, "/lines");

        //then
        assertAll(() -> assertThat(response.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value()),
                () -> assertThat(response.body().asString()).isEqualTo("line의 color(은)는 공백일 수 없습니다 입력된 값: []"));
    }

    @DisplayName("line 목록을 조회한다.")
    @Test
    void getLines() {
        /// given
        Long stationId1 = insertStationData("강남역");
        Long stationId2 = insertStationData("선릉역");
        Long lineId1 = insertLineData("신분당선", "red");
        Long lineId2 = insertLineData("2호선", "green");
        insertSectionData(lineId1, stationId1, stationId2);
        insertSectionData(lineId2, stationId1, stationId2);

        // when
        ExtractableResponse<Response> response = executeGetApi("/lines");

        List<Line> actualLines = response.jsonPath().getList(".", LineResponse.class).stream()
                .map(lineResponse -> new Line(lineResponse.getName(), lineResponse.getColor()))
                .collect(Collectors.toList());

        List<List<Station>> linesStations = response.jsonPath().getList(".", LineResponse.class).stream()
                .map(its -> its.getStations().stream()
                        .map(it -> new Station(it.getId(), it.getName())).collect(Collectors.toList()))
                .collect(Collectors.toList());

        // then
        assertAll(() -> assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value()),
                () -> assertThat(actualLines).containsAll(List.of(new Line("신분당선", "red"), new Line("2호선", "green"))),
                () -> assertThat(linesStations).isEqualTo(
                        List.of(List.of(new Station(stationId1, "강남역"), new Station(stationId2, "선릉역")),
                                List.of(new Station(stationId1, "강남역"), new Station(stationId2, "선릉역")))));
    }

    @DisplayName("id 를 이용하여 line 을 조회한다.")
    @Test
    void findLineById() {
        //given
        Long stationId1 = insertStationData("강남역");
        Long stationId2 = insertStationData("선릉역");
        Long lineId = insertLineData("신분당선", "red");
        insertSectionData(lineId, stationId1, stationId2);

        //when
        ExtractableResponse<Response> response = executeGetApi("/lines/" + lineId);

        Integer actualId = response.jsonPath().get("id");
        List<Station> expectStations = response.body().jsonPath().getList("stations", StationResponse.class)
                .stream().map(it -> new Station(it.getId(), it.getName()))
                .collect(Collectors.toList());

        //then
        assertAll(() -> assertThat(actualId).isEqualTo(lineId.intValue()),
                () -> assertThat(expectStations).isEqualTo(
                        List.of(new Station(stationId1, "강남역"), new Station(stationId2, "선릉역"))));
    }

    @DisplayName("id 를 이용하여 line 을 수정한다.")
    @Test
    void updateLine() {
        //given
        Long lineId = insertLineData("신분당선", "red");
        Line line = new Line("다른분당선", "black");

        //when
        ExtractableResponse<Response> response = executePutApi(line, "/lines/" + lineId);

        //then
        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
    }

    @DisplayName("id 를 이용하여 line 을 삭제한다.")
    @Test
    void deleteLineById() {
        //given
        Long lineId = insertLineData("신분당선", "red");

        //when
        ExtractableResponse<Response> response = executeDeleteApi("/lines/" + lineId);

        //then
        assertThat(response.statusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());
    }

    @DisplayName("line id와 stationIds 를 이용하여 section 을 저장한다.")
    @Test
    void saveSection() {
        //given
        Long lineId = insertLineData("신분당선", "red");
        Long stationId1 = insertStationData("강남역");
        Long stationId2 = insertStationData("선릉역");
        Long stationId3 = insertStationData("잠실역");
        insertSectionData(lineId, stationId1, stationId2);
        SectionRequest sectionRequest = new SectionRequest(stationId2, stationId3, 10);

        //when
        ExtractableResponse<Response> response = executePostSectionApi(sectionRequest,
                "/lines/" + lineId + "/sections");

        //then
        assertThat(response.statusCode()).isEqualTo(HttpStatus.CREATED.value());
    }

    @DisplayName("line id와 section id를 이용하여 section 를 삭제한다.")
    @Test
    void deleteSection() {
        //given
        Long lineId = insertLineData("신분당선", "red");
        Long stationId1 = insertStationData("강남역");
        Long stationId2 = insertStationData("선릉역");
        Long stationId3 = insertStationData("잠실역");
        insertSectionData(lineId, stationId1, stationId2);
        insertSectionData(lineId, stationId2, stationId3);

        Map<String, Object> param = new HashMap<>();
        param.put("stationId", stationId3);

        //when
        ExtractableResponse<Response> response = executeDeleteApiWithParam("/lines/" + lineId + "/stations", param);

        //then
        assertThat(response.statusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());
    }

    private Long insertLineData(String name, String color) {
        String insertSql = "insert into LINE (name, color) values (:name, :color)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource source = new MapSqlParameterSource();
        source.addValue("name", name);
        source.addValue("color", color);
        jdbcTemplate.update(insertSql, source, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    private Long insertStationData(String name) {
        String insertSql = "insert into STATION (name) values (:name)";
        SqlParameterSource source = new MapSqlParameterSource("name", name);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(insertSql, source, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    private Long insertSectionData(Long lineId, Long upStationId, Long downStationId) {
        String insertSql = "insert into SECTION (line_id, up_station_id, down_station_id, distance) values (:lineId, :upStationId, :downStationId, :distance)";
        SqlParameterSource source = new BeanPropertySqlParameterSource(
                new Section(lineId, upStationId, downStationId, 10));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(insertSql, source, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    private ExtractableResponse<Response> executeGetApi(String path) {
        return RestAssured.given().log().all()
                .when().get(path)
                .then().log().all().extract();
    }

    private ExtractableResponse<Response> executePostLineApi(LineRequest request, String path) {
        return RestAssured.given().log().all().body(request).contentType(MediaType.APPLICATION_JSON_VALUE)
                .when().post(path)
                .then().log().all().extract();
    }

    private ExtractableResponse<Response> executePostSectionApi(SectionRequest request, String path) {
        return RestAssured.given().log().all().body(request).contentType(MediaType.APPLICATION_JSON_VALUE)
                .when().post(path)
                .then().log().all().extract();
    }

    private ExtractableResponse<Response> executePutApi(Line line, String path) {
        return RestAssured.given().log().all()
                .body(line).contentType(MediaType.APPLICATION_JSON_VALUE)
                .when().put(path)
                .then().log().all().extract();
    }

    private ExtractableResponse<Response> executeDeleteApi(String path) {
        return RestAssured.given().log().all()
                .when().delete(path)
                .then().log().all().extract();
    }

    private ExtractableResponse<Response> executeDeleteApiWithParam(String path, Map<String, Object> param) {
        return RestAssured.given().log().all()
                .params(param)
                .when().delete(path)
                .then().log().all().extract();
    }
}
