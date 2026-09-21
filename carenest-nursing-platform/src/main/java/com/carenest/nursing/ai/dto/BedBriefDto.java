package com.carenest.nursing.ai.dto;

import com.carenest.nursing.domain.Bed;
import lombok.Data;

/**
 * 床位精简信息。
 *
 * @author qoder
 */
@Data
public class BedBriefDto {

    private Long id;

    private String bedNumber;

    /** 床位状态文案（未入住/已入住/入住申请中） */
    private String bedStatus;

    private Long roomId;

    /** 所属房间编号，便于模型直接回答“哪个房间” */
    private String roomCode;

    public static BedBriefDto from(Bed bed, String roomCode) {
        BedBriefDto dto = new BedBriefDto();
        dto.setId(bed.getId());
        dto.setBedNumber(bed.getBedNumber());
        dto.setBedStatus(AiText.bedStatus(bed.getBedStatus()));
        dto.setRoomId(bed.getRoomId());
        dto.setRoomCode(roomCode);
        return dto;
    }
}
