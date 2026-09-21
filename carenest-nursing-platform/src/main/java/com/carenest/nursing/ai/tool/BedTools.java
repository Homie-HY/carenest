package com.carenest.nursing.ai.tool;

import com.carenest.nursing.ai.dto.BedBriefDto;
import com.carenest.nursing.ai.dto.RoomOccupancyDto;
import com.carenest.nursing.domain.Bed;
import com.carenest.nursing.domain.Floor;
import com.carenest.nursing.domain.Room;
import com.carenest.nursing.service.IBedService;
import com.carenest.nursing.service.IFloorService;
import com.carenest.nursing.service.IRoomService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 床位与房间查询工具（只读）。
 * <p>
 * 床位、房间属于机构设施数据，不含老人隐私，所有已登录护理助手用户均可查询。
 *
 * @author qoder
 */
public class BedTools {

    /** 未入住 */
    private static final int BED_FREE = 0;
    /** 已入住 */
    private static final int BED_OCCUPIED = 1;

    private final IBedService bedService;
    private final IRoomService roomService;
    private final IFloorService floorService;

    public BedTools(IBedService bedService, IRoomService roomService, IFloorService floorService) {
        this.bedService = bedService;
        this.roomService = roomService;
        this.floorService = floorService;
    }

    @Tool("查询空闲（未入住）床位清单。可选按楼层名称过滤，例如 3楼；不传楼层则查询全部空闲床位。")
    public String listFreeBeds(@P(value = "楼层名称，例如 3楼；查全部则留空", required = false) String floorName) {
        List<Long> roomIdsFilter = null;
        if (!isBlank(floorName)) {
            Floor floor = floorService.lambdaQuery().eq(Floor::getName, floorName.trim()).one();
            if (floor == null) {
                return ToolResults.text("未找到名为 " + floorName + " 的楼层");
            }
            roomIdsFilter = roomService.lambdaQuery().eq(Room::getFloorId, floor.getId()).list()
                    .stream().map(Room::getId).collect(Collectors.toList());
            if (roomIdsFilter.isEmpty()) {
                return ToolResults.text(floorName + " 暂无房间");
            }
        }
        final List<Long> roomIds = roomIdsFilter;
        long total = bedService.lambdaQuery()
                .eq(Bed::getBedStatus, BED_FREE)
                .in(roomIds != null, Bed::getRoomId, roomIds)
                .count();
        if (total == 0) {
            return ToolResults.text(isBlank(floorName) ? "暂无空闲床位" : floorName + " 暂无空闲床位");
        }
        List<Bed> beds = bedService.lambdaQuery()
                .eq(Bed::getBedStatus, BED_FREE)
                .in(roomIds != null, Bed::getRoomId, roomIds)
                .orderByAsc(Bed::getBedNumber)
                .last("limit " + ToolResults.MAX_ITEMS)
                .list();
        Map<Long, String> roomCodeMap = roomCodesOf(beds);
        List<BedBriefDto> items = new ArrayList<>(beds.size());
        for (Bed bed : beds) {
            items.add(BedBriefDto.from(bed, roomCodeMap.get(bed.getRoomId())));
        }
        return ToolResults.page(total, items);
    }

    @Tool("根据房间编号查询该房间的入住情况，返回床位总数、空闲数、已入住数。")
    public String getRoomOccupancy(@P("房间编号，例如 104") String roomCode) {
        if (isBlank(roomCode)) {
            return ToolResults.text("请提供房间编号");
        }
        Room room = roomService.lambdaQuery().eq(Room::getCode, roomCode.trim()).one();
        if (room == null) {
            return ToolResults.text("未找到编号为 " + roomCode + " 的房间");
        }
        List<Bed> beds = bedService.lambdaQuery().eq(Bed::getRoomId, room.getId()).list();
        RoomOccupancyDto dto = new RoomOccupancyDto();
        dto.setRoomId(room.getId());
        dto.setRoomCode(room.getCode());
        dto.setTypeName(room.getTypeName());
        dto.setFloorId(room.getFloorId());
        dto.setTotalBeds(beds.size());
        int free = 0;
        int occupied = 0;
        for (Bed bed : beds) {
            if (bed.getBedStatus() != null && bed.getBedStatus() == BED_FREE) {
                free++;
            } else if (bed.getBedStatus() != null && bed.getBedStatus() == BED_OCCUPIED) {
                occupied++;
            }
        }
        dto.setFreeBeds(free);
        dto.setOccupiedBeds(occupied);
        return ToolResults.single(dto);
    }

    /**
     * 批量取床位所属房间编号，避免逐条查询。
     */
    private Map<Long, String> roomCodesOf(List<Bed> beds) {
        List<Long> roomIds = beds.stream().map(Bed::getRoomId)
                .filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (roomIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> map = new HashMap<>();
        for (Room room : roomService.lambdaQuery().in(Room::getId, roomIds).list()) {
            map.put(room.getId(), room.getCode());
        }
        return map;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
