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
    /**处理添加团队的请求。接收一个AddTeamRequest对象作为请求体，如果请求体为空，
     则抛出BusinessException异常。调用teamService的addTeam方法添加团队，并返回结果。**/
    @PostMapping("/add")
    public BaseResponse<Long> addTeam(@RequestBody AddTeamRequest addTeamRequest) {
        // 1. 请求参数是否为空？
        //首先通过判断参数addTeamRequest是否为空来判断请求参数是否合法，如果为空则抛出参数错误的异常。
        if (addTeamRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
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
     * @param teamQueryRequest 查询请求对象，包含搜索关键词等信息
     * @return 包含队伍列表的BaseResponse对象
     */
    @GetMapping("/list")
    public BaseResponse<List<TeamUserVO>> queryTeams(TeamQueryRequest teamQueryRequest) {
        // 校验用户登录状态
        User loginUser = userService.getLoginUser(teamQueryRequest.getUserAccount(), teamQueryRequest.getUuid());
        // 如果用户未登录，抛出无权限的异常
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NO_AUTH,"无权限");
        }
//        List<TeamUserVO> teamList = teamService.searchTeams(teamQueryRequest);
        // 调用service层方法，根据关键词搜索队伍
        List<TeamUserVO> teamList = teamService.queryTeams(teamQueryRequest);
        // 成功返回队伍列表
        return ResultUtils.success(teamList);
    }

    /**
     * 队伍列表（分页）
     * @param teamQueryRequest 分页查询请求对象，包含页数、每页条数等信息
     * @return 包含分页队伍数据的BaseResponse对象
     */
    @GetMapping("/list/page")
    public BaseResponse<Page<Team>> listTeamsByPage(TeamQueryRequest teamQueryRequest) {
        // 创建Team实体类对象，用于构建查询条件
        if (teamQueryRequest == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 创建Team实体类对象，用于构建查询条件
        Team team = new Team();
        // 创建查询包装器，用于动态SQL查询
        QueryWrapper<Team> teamQueryWrapper = new QueryWrapper<>(team);
        // 将查询请求对象的属性复制到Team实体类对象中，以便构建查询条件
        BeanUtils.copyProperties(teamQueryRequest,team);
        // 调用service层方法，执行分页查询
        Page<Team> teamPage = teamService.page(new Page<>(teamQueryRequest.getPageNum(), teamQueryRequest.getPageSize()),teamQueryWrapper);
        // 成功返回分页队伍数据
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
    @PostMapping("/quit")// 处理POST请求路径为"/quit"的方法
    public BaseResponse<Boolean> quitTeam(@RequestBody TeamQuitRequest teamQuitRequest) {
        // 检查请求参数是否为空，如果传入的teamQuitRequest为空，抛出参数错误的业务异常
        if (teamQuitRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 获取当前用户信息 从 teamQuitRequest 中获取用户账号和 UUID 并调用 userService 获取登录用户信息。
        String userAccount = teamQuitRequest.getUserAccount();
        String uuid = teamQuitRequest.getUuid();
        User loginUser = userService.getLoginUser(userAccount, uuid);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NO_AUTH,"未登录");
        }
        //  从 teamQuitRequest 中获取队伍 ID
        // 调用teamService中的quitTeam方法处理请求，并获取返回结果
        Boolean result = teamService.quitTeam( loginUser ,teamQuitRequest.getTeamId());
        // 使用ResultUtils.success方法封装返回结果并返回
        return ResultUtils.success(result);
    }

    /**
     * 解散队伍
     */
    @PostMapping("/disband")// 处理POST请求路径为"/disband"的方法
    public BaseResponse<Boolean>  disbandTeam(@RequestBody TeamDisbandRequest teamDisbandRequest) {
        // 如果传入的teamDisbandRequest为空，抛出参数错误的业务异常
        if (teamDisbandRequest == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String userAccount = teamDisbandRequest.getUserAccount();
        String uuid = teamDisbandRequest.getUuid();
        User loginUser = userService.getLoginUser(userAccount, uuid);

        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NO_AUTH,"未登录");
        }
        // 调用teamService中的disbandTeam方法处理请求，并获取返回结果
        Boolean result = teamService.disbandTeam(loginUser,teamDisbandRequest.getTeamId());
        // 使用ResultUtils.success方法封装返回结果并返回
        return ResultUtils.success(result);
    }


    /**
     * 获取我创建的队伍 处理HTTP GET请求
     *
     * @param teamQueryRequest 请求参数，包含用户账户、UUID等信息
     * @return 包含当前用户创建的所有队伍信息的BaseResponse对象
     */
    @GetMapping("/list/my/create")
    public BaseResponse<List<TeamUserVO>> listMyCreateTeams(TeamQueryRequest teamQueryRequest) {
        // 如果请求参数为空，抛出参数错误的BusinessException
        if (teamQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 判断是否为当前用户
        User loginUser = userService.getLoginUser(teamQueryRequest.getUserAccount(), teamQueryRequest.getUuid());
        // 设置当前用户的ID到请求参数中
        teamQueryRequest.setUserId(loginUser.getId());

//        List<TeamUserVO> teamList = teamService.listMyCreateTeams(teamQueryRequest);
        // 从teamService中查询当前用户创建的队伍信息
        List<TeamUserVO> teamList = teamService.queryTeams(teamQueryRequest);
        // 构造成功响应，返回查询到的队伍列表
        return ResultUtils.success(teamList);
    }


    /**
     * 获取我加入的队伍 处理HTTP GET请求
     *
     * @param teamQueryRequest 请求参数，包含用户账户、UUID等信息
     * @return 包含当前用户加入的所有队伍信息的BaseResponse对象
     */
    @GetMapping("/list/my/join")
    public BaseResponse<List<TeamUserVO>> listMyJoinTeams(TeamQueryRequest teamQueryRequest) {
        if (teamQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        User loginUser = userService.getLoginUser(teamQueryRequest.getUserAccount(), teamQueryRequest.getUuid());
        // 获取当前用户的ID
        Long userId = loginUser.getId();
        // 去关系表中获取teamIdList
        // 准备查询条件，获取用户加入的所有队伍
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(UserTeam::getUserId,userId);

        // 从userTeamService中查询当前用户加入的所有队伍id列表
        List<UserTeam> userTeamList = userTeamService.list(queryWrapper);
        // 使用Stream API和Collectors.groupingBy方法，按队伍ID分组，去除重复的队伍信息
        Map<Long, List<UserTeam>> listMap = userTeamList.stream().collect(Collectors.groupingBy(UserTeam::getTeamId));
        // 提取去重后的队伍ID列表
        ArrayList<Long> idList = new ArrayList<>(listMap.keySet());
        // 如果ID列表为空，直接构造成功响应，返回null
        if (CollectionUtils.isEmpty(idList)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"队伍ID列表为空");
        }
        // 设置查询条件中的队伍ID列表
        teamQueryRequest.setIdList(idList);
//        List<TeamUserVO> teamList = teamService.listMyJoinTeams(teamQueryRequest);
        // 从teamService中查询当前用户加入的队伍信息
        List<TeamUserVO> teamList = teamService.queryTeams(teamQueryRequest);
        // 构造成功响应，返回查询到的队伍列表
        return ResultUtils.success(teamList);
    }
}



























