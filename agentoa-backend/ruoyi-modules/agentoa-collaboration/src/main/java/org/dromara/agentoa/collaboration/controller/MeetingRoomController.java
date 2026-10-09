package org.dromara.agentoa.collaboration.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.collaboration.domain.bo.RoomBo;
import org.dromara.agentoa.collaboration.domain.vo.RoomVo;
import org.dromara.agentoa.collaboration.service.IMeetingRoomService;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 会议室接口（API 规范 9.2）：维护需 cl:room:*，列表对登录用户开放。 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/calendar/rooms")
public class MeetingRoomController {

    private final IMeetingRoomService roomService;

    @GetMapping
    public R<PageVo<RoomVo>> list(@RequestParam(required = false) String keyword,
                                  @RequestParam(required = false) Integer pageNum,
                                  @RequestParam(required = false) Integer pageSize) {
        return R.ok(roomService.list(keyword, pageNum, pageSize));
    }

    @GetMapping("/all")
    public R<List<RoomVo>> all() {
        return R.ok(roomService.all());
    }

    @GetMapping("/{id}")
    public R<RoomVo> detail(@PathVariable Long id) {
        return R.ok(roomService.get(id));
    }

    @SaCheckPermission("cl:room:add")
    @Log(title = "会议室", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<RoomVo> add(@Validated @RequestBody RoomBo bo) {
        return R.ok(roomService.create(bo, LoginHelper.getUserId()));
    }

    @SaCheckPermission("cl:room:edit")
    @Log(title = "会议室", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{id}")
    public R<RoomVo> edit(@PathVariable Long id, @Validated @RequestBody RoomBo bo) {
        return R.ok(roomService.update(id, bo, LoginHelper.getUserId()));
    }

    @SaCheckPermission("cl:room:remove")
    @Log(title = "会议室", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        roomService.delete(id, LoginHelper.getUserId());
        return R.ok();
    }
}
