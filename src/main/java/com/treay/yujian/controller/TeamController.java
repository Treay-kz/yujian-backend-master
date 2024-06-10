package com.treay.yujian.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.treay.yujian.common.BaseResponse;

import com.treay.yujian.common.ErrorCode;
import com.treay.yujian.common.ResultUtils;
import com.treay.yujian.exception.BusinessException;
import com.treay.yujian.model.request.*;
import com.treay.yujian.service.TeamService;
import com.treay.yujian.service.UserTeamService;
import com.treay.yujian.model.domain.Team;
import com.treay.yujian.model.domain.User;
import com.treay.yujian.model.domain.UserTeam;
import com.treay.yujian.model.vo.TeamUserVO;
import com.treay.yujian.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.websocket.server.PathParam;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 队伍接口
 *
 * @author Treay
 * 
 */
@RestController
@RequestMapping("/team")
@CrossOrigin(origins = {"http://localhost:3000","http://yujian.treay.cn"},allowCredentials="true")
@Slf4j
public class TeamController {

    @Resource
    private UserService userService;

    @Resource
    private TeamService teamService;

    @Resource
    private UserTeamService userTeamService;

    /**
     * 创建队伍
     * @param addTeamRequest
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addTeam(@RequestBody AddTeamRequest addTeamRequest) {
        if (addTeamRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(addTeamRequest.getUserAccount(), addTeamRequest.getUuid());
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        long result = teamService.addTeam(addTeamRequest);
        return ResultUtils.success(result);
    }

    /**
     * 更新队伍
     * @param teamUpdateRequest
     * @return
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateTeam(@RequestBody TeamUpdateRequest teamUpdateRequest) {
        if (teamUpdateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(teamUpdateRequest.getUserAccount(), teamUpdateRequest.getUuid());
        if (loginUser == null){
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        boolean result = teamService.updateTeam(teamUpdateRequest, loginUser);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新失败");
        }
        return ResultUtils.success(true);
    }

    /**
     * 通过id获取队伍
     * @param id
     * @return
     */
    @GetMapping("/get")
    public BaseResponse<Team> getTeamById(@RequestParam("id") long id) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Team team = teamService.getById(id);
        if (team == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR);
        }
        return ResultUtils.success(team);
    }

    /**
     * 根据关键词搜索队伍
     * @param teamQueryRequest
     * @return
     */
    @GetMapping("/list")
    public BaseResponse<List<TeamUserVO>> queryTeams(TeamQueryRequest teamQueryRequest) {
        User loginUser = userService.getLoginUser(teamQueryRequest.getUserAccount(), teamQueryRequest.getUuid());
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NO_AUTH,"无权限");
        }
//        List<TeamUserVO> teamList = teamService.searchTeams(teamQueryRequest);
        List<TeamUserVO> teamList = teamService.queryTeams(teamQueryRequest);
        return ResultUtils.success(teamList);
    }

    /**
     * 队伍列表（分页）
     * @param teamQueryRequest
     * @return
     */
    @GetMapping("/list/page")
    public BaseResponse<Page<Team>> listTeamsByPage(TeamQueryRequest teamQueryRequest) {
        if (teamQueryRequest == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Team team = new Team();
        QueryWrapper<Team> teamQueryWrapper = new QueryWrapper<>(team);
        BeanUtils.copyProperties(teamQueryRequest,team);
        Page<Team> teamPage = teamService.page(new Page<>(teamQueryRequest.getPageNum(), teamQueryRequest.getPageSize()),teamQueryWrapper);
        return ResultUtils.success(teamPage);
    }

    /**
     * 加入队伍
     * @param teamJoinRequest
     * @return
     */
    @PostMapping("/join")
    public BaseResponse<Boolean> joinTeam(@RequestBody TeamJoinRequest teamJoinRequest) {
        if (teamJoinRequest == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(teamJoinRequest.getUserAccount(), teamJoinRequest.getUuid());
        Boolean result = teamService.joinTeam(loginUser,teamJoinRequest.getTeamId(),teamJoinRequest.getPassword());
        return ResultUtils.success(result);
    }

    /**
     * 退出队伍
     * @param teamQuitRequest
     * @return
     */
    @PostMapping("/quit")
    public BaseResponse<Boolean> quitTeam(@RequestBody TeamQuitRequest teamQuitRequest) {
        if (teamQuitRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String userAccount = teamQuitRequest.getUserAccount();
        String uuid = teamQuitRequest.getUuid();
        User loginUser = userService.getLoginUser(userAccount, uuid);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NO_AUTH,"未登录");
        }
        Boolean result = teamService.quitTeam( loginUser ,teamQuitRequest.getTeamId());
        return ResultUtils.success(result);
    }

    /**
     * 解散队伍
     */
    @PostMapping("/disband")
    public BaseResponse<Boolean>  disbandTeam(@RequestBody TeamDisbandRequest teamDisbandRequest) {
        if (teamDisbandRequest == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Boolean result = teamService.disbandTeam(teamDisbandRequest);
        return ResultUtils.success(result);
    }


    /**
     * 获取我创建的队伍
     *
     * @param teamQueryRequest
     * @return
     */
    @GetMapping("/list/my/create")
    public BaseResponse<List<TeamUserVO>> listMyCreateTeams(TeamQueryRequest teamQueryRequest) {
        if (teamQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 判断是否为当前用户
        User loginUser = userService.getLoginUser(teamQueryRequest.getUserAccount(), teamQueryRequest.getUuid());
        teamQueryRequest.setUserId(loginUser.getId());

//        List<TeamUserVO> teamList = teamService.listMyCreateTeams(teamQueryRequest);
        List<TeamUserVO> teamList = teamService.queryTeams(teamQueryRequest);
        return ResultUtils.success(teamList);
    }


    /**
     * 获取我加入的队伍
     *
     * @param teamQueryRequest
     * @return
     */
    @GetMapping("/list/my/join")
    public BaseResponse<List<TeamUserVO>> listMyJoinTeams(TeamQueryRequest teamQueryRequest) {
        if (teamQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 去关系表中获取teamIdList
        User loginUser = userService.getLoginUser(teamQueryRequest.getUserAccount(), teamQueryRequest.getUuid());
        Long userId = loginUser.getId();
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(UserTeam::getUserId,userId);
        List<UserTeam> userTeamList = userTeamService.list(queryWrapper);
        //去重
        Map<Long, List<UserTeam>> listMap = userTeamList.stream().collect(Collectors.groupingBy(UserTeam::getTeamId));
        ArrayList<Long> idList = new ArrayList<>(listMap.keySet());
        if (CollectionUtils.isEmpty(idList)){
            return ResultUtils.success(null);
        }
        teamQueryRequest.setIdList(idList);
//        List<TeamUserVO> teamList = teamService.listMyJoinTeams(teamQueryRequest);
        List<TeamUserVO> teamList = teamService.queryTeams(teamQueryRequest);
        return ResultUtils.success(teamList);
    }
}



























