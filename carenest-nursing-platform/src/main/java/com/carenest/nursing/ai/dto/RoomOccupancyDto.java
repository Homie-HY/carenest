package com.carenest.nursing.ai.dto;

import lombok.Data;

/**
 * 房间入住情况汇总。
 *
 * @author qoder
 */
@Data
public class RoomOccupancyDto {

    private Long roomId;

    private String roomCode;

    private String typeName;

    private Long floorId;

    /** 床位总数 */
    private int totalBeds;

    /** 空闲床位数（未入住） */
    private int freeBeds;

    /** 已入住床位数 */
    private int occupiedBeds;
}
