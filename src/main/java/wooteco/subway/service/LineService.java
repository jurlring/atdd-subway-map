package wooteco.subway.service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wooteco.subway.dao.LineDao;
import wooteco.subway.dao.SectionDao;
import wooteco.subway.dao.StationDao;
import wooteco.subway.domain.Line;
import wooteco.subway.domain.Section;
import wooteco.subway.domain.Sections;
import wooteco.subway.domain.Station;
import wooteco.subway.dto.LineRequest;
import wooteco.subway.dto.LineResponse;
import wooteco.subway.dto.SectionRequest;
import wooteco.subway.dto.StationResponse;

@Service
@Transactional
public class LineService {

    private static final int MINIMUM_SECTIONS_SIZE = 1;

    private final StationDao stationDao;
    private final LineDao lineDao;
    private final SectionDao sectionDao;

    public LineService(StationDao stationDao, LineDao lineDao, SectionDao sectionDao) {
        this.stationDao = stationDao;
        this.lineDao = lineDao;
        this.sectionDao = sectionDao;
    }

    public LineResponse saveLine(LineRequest lineRequest) {
        validDuplicatedLine(lineRequest.getName(), lineRequest.getColor());
        Long id = lineDao.save(lineRequest.toLine());

        Section section = new Section(id, lineRequest.getUpStationId(), lineRequest.getDownStationId(),
                lineRequest.getDistance());
        sectionDao.save(section);
        return new LineResponse(id, lineRequest.getName(), lineRequest.getColor(), findStationsBySection(section));
    }

    private void validDuplicatedLine(String name, String color) {
        if (lineDao.existByName(name) || lineDao.existByColor(color)) {
            throw new IllegalArgumentException("중복된 Line 이 존재합니다.");
        }
    }

    private List<StationResponse> findStationsBySection(Section section) {
        Station upStation = stationDao.findById(section.getUpStationId())
                .orElseThrow(() -> new IllegalStateException("상행 역이 존재하지 않습니다."));
        Station downStation = stationDao.findById(section.getDownStationId())
                .orElseThrow(() -> new IllegalStateException("하행 역이 존재하지 않습니다."));

        return Stream.of(upStation, downStation)
                .map(StationResponse::new)
                .collect(Collectors.toList());
    }

    public void saveSection(Long lineId, SectionRequest sectionRequest) {
        Sections sections = new Sections(sectionDao.findByLineId(lineId));
        validSections(sections, sectionRequest);
        if (sections.isForkSection(sectionRequest)) {
            processBiDirectionSection(sectionRequest, sections);
        }
        sectionDao.save(sectionRequest.toSection(lineId));
    }

    private void validSections(Sections sections, SectionRequest sectionRequest) {
        sections.validSameStations(sectionRequest);
        sections.validNonLinkSection(sectionRequest);
    }

    private void processBiDirectionSection(SectionRequest sectionRequest, Sections sections) {
        sections.findDownSection(sectionRequest.getUpStationId())
                .ifPresent(section -> processUpDirectionSection(section, sectionRequest));
        sections.findUpSection(sectionRequest.getDownStationId())
                .ifPresent(section -> processDownDirectionSection(section, sectionRequest));
    }

    private void processUpDirectionSection(Section section, SectionRequest sectionRequest) {
        validSectionDistance(sectionRequest, section);
        sectionDao.save(new Section(section.getLineId(), sectionRequest.getDownStationId(), section.getDownStationId(),
                section.getDistance() - sectionRequest.getDistance()));
        sectionDao.deleteById(section.getId());
    }

    private void processDownDirectionSection(Section section, SectionRequest sectionRequest) {
        validSectionDistance(sectionRequest, section);
        sectionDao.save(new Section(section.getLineId(), section.getUpStationId(), sectionRequest.getUpStationId(),
                section.getDistance() - sectionRequest.getDistance()));
        sectionDao.deleteById(section.getId());
    }

    private void validSectionDistance(SectionRequest sectionRequest, Section section) {
        if (section.isOverDistance(sectionRequest.getDistance())) {
            throw new IllegalArgumentException("추가될 구간의 길이가 기존 구간의 길이보다 깁니다.");
        }
    }

    @Transactional(readOnly = true)
    public List<LineResponse> findLines() {
        return lineDao.findAll().stream()
                .map(this::findLineResponseByLine)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public LineResponse findLineById(Long id) {
        Line line = lineDao.findById(id).orElseThrow(() -> new IllegalStateException("해당 ID를 가진 노선이 존재하지 않습니다."));
        return findLineResponseByLine(line);
    }

    private LineResponse findLineResponseByLine(Line line) {
        Sections sections = new Sections(sectionDao.findByLineId(line.getId()));
        List<Long> stationIdsInOrder = sections.findStationIdsInOrder();
        List<StationResponse> stationResponses = stationIdsInOrder.stream()
                .filter(id -> stationDao.findById(id).orElse(null) != null)
                .map(id -> new StationResponse(stationDao.findById(id).get()))
                .collect(Collectors.toUnmodifiableList());
        return new LineResponse(line, stationResponses);
    }

    public void updateLine(Long id, LineRequest lineRequest) {
        validDuplicatedLine(lineRequest.getName(), lineRequest.getColor());
        lineDao.update(id, lineRequest);
    }

    public void deleteLine(Long id) {
        lineDao.deleteById(id);
    }

    public void deleteSection(Long lineId, Long stationId) {
        validSectionSize(lineId);
        linkSection(lineId, stationId);
        sectionDao.deleteByLineIdAndStationId(lineId, stationId);
    }

    private void validSectionSize(Long lineId) {
        List<Section> sections = sectionDao.findByLineId(lineId);
        if (sections.size() <= MINIMUM_SECTIONS_SIZE) {
            throw new IllegalArgumentException("노선에 구간이 1개 이상은 존재해야합니다.");
        }
    }

    private void linkSection(Long lineId, Long stationId) {
        Sections sections = new Sections(sectionDao.findByLineId(lineId));
        if (sections.requiredLink(stationId)) {
            sections.findUpSection(stationId).ifPresent(section -> sectionDao.deleteById(section.getId()));
            sections.findDownSection(stationId).ifPresent(section -> sectionDao.deleteById(section.getId()));
            sectionDao.save(sections.findLinkSection(lineId, stationId));
        }
    }
}
