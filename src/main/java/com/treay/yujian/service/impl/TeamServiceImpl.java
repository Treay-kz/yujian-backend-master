package com.treay.yujian.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.treay.yujian.common.ErrorCode;
import com.treay.yujian.common.ResultUtils;
import com.treay.yujian.exception.BusinessException;
import com.treay.yujian.mapper.TeamMapper;
import com.treay.yujian.model.dto.TeamQuery;
import com.treay.yujian.model.enums.TeamStatusEnum;
import com.treay.yujian.model.request.*;
import com.treay.yujian.service.TeamService;
import com.treay.yujian.service.UserTeamService;
import com.treay.yujian.model.domain.User;
import com.treay.yujian.model.domain.UserTeam;
import com.treay.yujian.model.vo.TeamUserVO;
import com.treay.yujian.model.vo.UserVO;
import com.treay.yujian.model.domain.Team;
import com.treay.yujian.service.UserService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 队伍服务实现类
 *
 * @author Treay
 *
 */
@Service
public class TeamServiceImpl extends ServiceImpl<TeamMapper, Team>
        implements TeamService {

    @Resource
    private UserTeamService userTeamService;

    @Resource
    private UserService userService;

    @Resource
    private RedissonClient redissonClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long addTeam(AddTeamRequest addTeamRequest) {
        // 1. 请求参数是否为空？
        if (addTeamRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 2. 是否登录，未登录不允许创建
        User loginUser = userService.getLoginUser(addTeamRequest.getUserAccount(), addTeamRequest.getUuid());
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        final long userId = loginUser.getId();
        // 3. 校验信息
        //   1. 队伍人数 > 1 且 <= 20
        int maxNum = Optional.ofNullable(addTeamRequest.getMaxNum()).orElse(0);
        if (maxNum < 1 || maxNum > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍人数不满足要求");
        }
        //   2. 队伍标题 <= 20
        String name = addTeamRequest.getName();
        if (StringUtils.isBlank(name) || name.length() > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍标题不满足要求");
        }
        //   3. 描述 <= 512
        String description = addTeamRequest.getDescription();
        if (StringUtils.isNotBlank(description) && description.length() > 512) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍描述过长");
        }
        //   4. status 是否公开（int）不传默认为 0（公开）
        int status = Optional.ofNullable(addTeamRequest.getStatus()).orElse(0);
        TeamStatusEnum statusEnum = TeamStatusEnum.getEnumByValue(status);
        if (statusEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍状态不满足要求");
        }
        //   5. 如果 status 是加密状态，一定要有密码，且密码 <= 32
        String password = addTeamRequest.getPassword();
        if (TeamStatusEnum.SECRET.equals(statusEnum)) {
            if (StringUtils.isBlank(password) || password.length() > 32) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码设置不正确");
            }
        }
        // 6. 超时时间 > 当前时间
        Date expireTime = addTeamRequest.getExpireTime();
        if (new Date().after(expireTime)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "超时时间 > 当前时间");
        }
        // 7. 校验用户最多创建 5 个队伍
        Team team = new Team();
        BeanUtils.copyProperties(addTeamRequest, team);
        QueryWrapper<Team> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(Team::getUserId, userId);
        long hasTeamNum = this.count(queryWrapper);
        if (hasTeamNum >= 5) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户最多创建 5 个队伍");
        }

        // 8. 插入队伍信息到队伍表
        team.setId(null);
        team.setUserId(userId);
        boolean result = this.save(team);
        Long teamId = team.getId();
        if (!result || teamId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "创建队伍失败");
        }
        // 9. 插入用户  => 队伍关系到关系表
        UserTeam userTeam = new UserTeam();
        userTeam.setUserId(userId);
        userTeam.setTeamId(teamId);
        userTeam.setJoinTime(new Date());
        result = userTeamService.save(userTeam);
        if (!result) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "创建队伍失败");
        }
        return teamId;
    }



    @Override
    public boolean updateTeam(TeamUpdateRequest teamUpdateRequest, User loginUser) {
        if (teamUpdateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long id = teamUpdateRequest.getId();
        Team oldTeam = getTeamById(id);
        //不是管理员也不是创建者
        boolean isAdmin = userService.isAdmin(loginUser);
        if (!oldTeam.getUserId().equals(loginUser.getId()) && !isAdmin) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        TeamStatusEnum oldStatusEum = TeamStatusEnum.getEnumByValue(teamUpdateRequest.getStatus());
        //状态不为加密才走判断

        if (!oldStatusEum.equals(TeamStatusEnum.SECRET)) {
            TeamStatusEnum statusEnum = TeamStatusEnum.getEnumByValue(teamUpdateRequest.getStatus());
            if (statusEnum.equals(TeamStatusEnum.SECRET)) {
                if (StringUtils.isBlank(teamUpdateRequest.getPassword())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "必须要有密码");
                }
            }
        }

        if (oldStatusEum.equals(TeamStatusEnum.PUBLIC)){
            teamUpdateRequest.setPassword("");
        }

        Team updateTeam = new Team();
        BeanUtils.copyProperties(teamUpdateRequest, updateTeam);
        return this.updateById(updateTeam);
    }


    @Override
    public Boolean joinTeam(TeamJoinRequest teamJoinRequest) {
        User loginUser = userService.getLoginUser(teamJoinRequest.getUserAccount(), teamJoinRequest.getUuid());

        Long teamId = teamJoinRequest.getTeamId();

        if (teamId == null || teamId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Team team = this.getTeamById(teamId);

        Date expireTime = team.getExpireTime();
        if (expireTime != null && expireTime.before(new Date())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍已过期");
        }

        Integer status = team.getStatus();
        TeamStatusEnum teamStatusEnum = TeamStatusEnum.getEnumByValue(status);
        if (TeamStatusEnum.PRIVATE.equals(teamStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "禁止加入私有的队伍");
        }

        String password = teamJoinRequest.getPassword();
        if (TeamStatusEnum.SECRET.equals(teamStatusEnum)) {
            if (StringUtils.isBlank(password)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "必须要有密码才能加入");
            }
        }

        if (TeamStatusEnum.SECRET.equals(teamStatusEnum)) {
            if (StringUtils.isBlank(password) || !team.getPassword().equals(password)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码不匹配");
            }
        }
        long userId = loginUser.getId();
        RLock lock = redissonClient.getLock("yujian:join_team:lock");
        try {
            //只有一个线程会获取锁
            while (true) {
                if (lock.tryLock(0, -1, TimeUnit.MILLISECONDS)) {
                    QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
                    queryWrapper.lambda().eq(UserTeam::getUserId, userId);
                    List<UserTeam> hasJoinTeams = userTeamService.list(queryWrapper);
                    if (hasJoinTeams.size() == 5) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "最多创建和加入五个队伍");
                    }
                    //已加入队伍的成员
                    List<UserTeam> userTeams = this.hasJoinTeamUser(teamId);
                    if (userTeams.size() == team.getMaxNum()) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "只能加入未满的队伍");
                    }

                    //不能重复加入已加入的队伍
                    //已加入队伍的id
                    ArrayList<Long> hasJoinTeamId = new ArrayList<>();
                    hasJoinTeams.forEach(t -> {
                        hasJoinTeamId.add(t.getTeamId());
                    });
                    if (hasJoinTeamId.contains(teamId)) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "不能重复加入");
                    }

                    //修改队伍信息
                    UserTeam userTeam = new UserTeam();
                    userTeam.setUserId(userId);
                    userTeam.setTeamId(teamId);
                    userTeam.setJoinTime(new Date());
                    return userTeamService.save(userTeam);
                }
            }
        } catch (InterruptedException e) {
            log.error("doCacheRecommendUser error", e);
            return  false;
        } finally {
            //只能释放自己的锁
            if (lock.isHeldByCurrentThread()) {
                System.out.println("unLock: " + Thread.currentThread().getId());
                lock.unlock();
            }
        }

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean quitTeam(TeamQuitRequest teamQuitRequest) {

        if (teamQuitRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        //当前用户
        String userAccount = teamQuitRequest.getUserAccount();
        String uuid = teamQuitRequest.getUuid();
        User loginUser = userService.getLoginUser(userAccount, uuid);

        Long teamId = teamQuitRequest.getTeamId();
        Team team = getTeamById(teamId);

        //查询是否加入队伍
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
        Long userId = loginUser.getId();
        queryWrapper.lambda().eq(UserTeam::getTeamId, teamId).eq(UserTeam::getUserId, userId);
        long count = userTeamService.count(queryWrapper);
        //没加入队伍直接抛异常
        if (count == 0) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "未加入队伍");
        }

        List<UserTeam> userTeams = this.hasJoinTeamUser(teamId);
        //队伍只剩下一人，删除队伍关系，删除队伍
        if (userTeams.size() == 1) {
            this.removeById(teamId);
        } else {
            //队伍人数大于一人
            //如果是队长退出则将队伍创建人转移给第二加入的成员
            if (Objects.equals(team.getUserId(), userId)) {
                Team tempTeam = new Team();
                tempTeam.setId(teamId);
                userTeams.sort(Comparator.comparing(UserTeam::getJoinTime));
                tempTeam.setUserId(userTeams.get(1).getUserId());
                //更新队伍队长
                boolean result = this.updateById(tempTeam);
                if (!result) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新队长失败");
                }
            }
            //如果不是队长直接退出队伍，删除用户-队伍关系
        }
        return userTeamService.remove(queryWrapper);
    }
    @Override
    public List<TeamUserVO> queryTeams(TeamQueryRequest teamQueryRequest) {
        //1. 从请求参数中取出队伍名称等查询条件，如果存在则作为查询条件
        //当前登录用户
        User loginUser = userService.getLoginUser(teamQueryRequest.getUserAccount(), teamQueryRequest.getUuid());

        QueryWrapper<Team> queryWrapper = new QueryWrapper<>();

        queryWrapper.lambda()
                .eq(teamQueryRequest.getId() != null && teamQueryRequest.getId() > 0, Team::getId, teamQueryRequest.getId())
                .in(!com.baomidou.mybatisplus.core.toolkit.CollectionUtils.isEmpty(teamQueryRequest.getIdList()), Team::getId, teamQueryRequest.getIdList())
                .like(StringUtils.isNotBlank(teamQueryRequest.getName()), Team::getName, teamQueryRequest.getName())
                .like(StringUtils.isNotBlank(teamQueryRequest.getDescription()), Team::getDescription, teamQueryRequest.getDescription())
                .apply(teamQueryRequest.getMaxNum() != null && teamQueryRequest.getMaxNum() <= 10, "max_num <= {0}", teamQueryRequest.getMaxNum())
                .eq(teamQueryRequest.getUserId() != null && teamQueryRequest.getUserId() > 0, Team::getUserId, teamQueryRequest.getUserId());

        if (StringUtils.isNotBlank(teamQueryRequest.getSearchText())) {
            queryWrapper.lambda()
                    .like(Team::getName, teamQueryRequest.getSearchText())
                    .or()
                    .like(Team::getDescription, teamQueryRequest.getSearchText());
        }
        // 过期时间大于当前日期或永不过期的过期代码
        queryWrapper.lambda().and(qw -> qw.gt(Team::getExpireTime, new Date()).or().isNull(Team::getExpireTime));

        Integer status = teamQueryRequest.getStatus();
        TeamStatusEnum statusEnum = TeamStatusEnum.getEnumByValue(status);
        if (statusEnum != null && (statusEnum.equals(TeamStatusEnum.PUBLIC) || statusEnum.equals(TeamStatusEnum.SECRET))) {
            queryWrapper.lambda().eq(Team::getStatus, status);
        }
        List<Team> teamList= this.list(queryWrapper);
        //加入私有队伍


        if (CollectionUtils.isEmpty(teamList)) {
            return new ArrayList<>();
        }
        List<TeamUserVO> respTeamUserVO = new ArrayList<>();
        //关联查询用户信息并脱敏
        for (Team team : teamList) {
            if (team.getExpireTime() == null || team.getExpireTime().after(new Date())) {
                TeamUserVO teamUserVO = new TeamUserVO();
                BeanUtils.copyProperties(team, teamUserVO);
                ArrayList<User> userList = new ArrayList<>();
                ArrayList<Long> memberId = new ArrayList<>();
                Long teamId = team.getId();
                QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
                userTeamQueryWrapper.lambda().eq(UserTeam::getTeamId, teamId);
                List<UserTeam> list = userTeamService.list(userTeamQueryWrapper);
                for (UserTeam userTeam : list) {
                    User user = userService.getById(userTeam.getUserId());
                    User safetyUser = userService.getSafetyUser(user);
                    userList.add(safetyUser);

                    //所有加入队伍的成员id
                    memberId.add(user.getId());
                    teamUserVO.setUserList(userList);
                }
                User userById = userService.getById(team.getUserId());
                teamUserVO.setCreateUsername(userById.getUsername());
                teamUserVO.setCreateAvatarUrl(userById.getAvatarUrl());
                teamUserVO.setCreateUser(userById);
                teamUserVO.setMemberId(memberId);
                Long userId = loginUser.getId();
                teamUserVO.setIsJoin(memberId.contains(userId));
                respTeamUserVO.add(teamUserVO);
            }

        }
        return respTeamUserVO;
    }



    /**
     * 获取我创建的队伍
     * @param teamQueryRequest
     * @return
     */
    @Override
    public List<TeamUserVO> listMyCreateTeams(TeamQueryRequest teamQueryRequest) {
        Long userId = teamQueryRequest.getUserId();
        QueryWrapper<Team> queryWrapper = new QueryWrapper<>();
        if (userId == null && userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户id错误");
        }
        queryWrapper.lambda().eq(Team::getUserId, teamQueryRequest.getUserId());

        queryWrapper.lambda().and(qw -> qw.gt(Team::getExpireTime, new Date()).or().isNull(Team::getExpireTime));
        List<Team> teamList = this.list(queryWrapper);
        List<TeamUserVO> teamUserVO = getTeamUserVO(teamList, userId);
        return teamUserVO;
    }

    /**
     * 获取我加入的队伍
     * @param teamQueryRequest
     * @return
     */
    @Override
    public List<TeamUserVO> listMyJoinTeams(TeamQueryRequest teamQueryRequest) {
        Long userId = teamQueryRequest.getUserId();
        QueryWrapper<Team> queryWrapper = new QueryWrapper<>();
        if (CollectionUtils.isEmpty(teamQueryRequest.getIdList())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"队伍id不能为空");
        }
        queryWrapper.lambda().in(Team::getId, teamQueryRequest.getIdList());

        queryWrapper.lambda().and(qw -> qw.gt(Team::getExpireTime, new Date()).or().isNull(Team::getExpireTime));
        List<Team> teamList = this.list(queryWrapper);
        List<TeamUserVO> teamUserVO = getTeamUserVO(teamList, userId);
        return teamUserVO;
    }

    /**
     * 根据关键词搜索队伍
     * @param teamQueryRequest
     * @return
     */
    @Override
    public List<TeamUserVO> searchTeams(TeamQueryRequest teamQueryRequest) {
        Long userId = teamQueryRequest.getUserId();
        QueryWrapper<Team> queryWrapper = new QueryWrapper<>();
        // 关键词查询
        if (StringUtils.isNotBlank(teamQueryRequest.getSearchText())) {
            queryWrapper.lambda()
                    .like(Team::getName, teamQueryRequest.getSearchText())
                    .or()
                    .like(Team::getDescription, teamQueryRequest.getSearchText());
        }
        // 非过期队伍
        queryWrapper.lambda().and(qw -> qw.gt(Team::getExpireTime, new Date()).or().isNull(Team::getExpireTime));
        Integer status = teamQueryRequest.getStatus();
        TeamStatusEnum statusEnum = TeamStatusEnum.getEnumByValue(status);
        if (statusEnum != null && (statusEnum.equals(TeamStatusEnum.PUBLIC) || statusEnum.equals(TeamStatusEnum.SECRET))) {
            queryWrapper.lambda().eq(Team::getStatus, status);
        }
        queryWrapper.lambda().in(Team::getId, teamQueryRequest.getIdList());
        List<Team> teamList = this.list(queryWrapper);
        List<TeamUserVO> teamUserVO = getTeamUserVO(teamList, userId);
        return teamUserVO;
    }

    /**
     * 获取脱敏后并且关联了队伍成员信息的TeamUserVO
     * @param teamList
     * @param userId
     * @return
     */
    public  List<TeamUserVO>  getTeamUserVO(List<Team> teamList,Long userId){
        List<TeamUserVO> resTeamUserVO = new ArrayList<>();
        for (Team team : teamList) {
            if (team.getExpireTime() == null || team.getExpireTime().after(new Date())) {
                TeamUserVO teamUserVO = new TeamUserVO();
                BeanUtils.copyProperties(team, teamUserVO);

                ArrayList<User> userList = new ArrayList<>();
                ArrayList<Long> memberId = new ArrayList<>();

                Long teamId = team.getId();
                QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
                userTeamQueryWrapper.lambda().eq(UserTeam::getTeamId, teamId);
                List<UserTeam> list = userTeamService.list(userTeamQueryWrapper);
                for (UserTeam userTeam : list) {
                    User user = userService.getById(userTeam.getUserId());
                    User safetyUser = userService.getSafetyUser(user);
                    userList.add(safetyUser);
                    //所有加入队伍的成员id
                    memberId.add(user.getId());
                    teamUserVO.setUserList(userList);
                }
                User userById = userService.getById(team.getUserId());
                teamUserVO.setCreateUsername(userById.getUsername());
                teamUserVO.setCreateAvatarUrl(userById.getAvatarUrl());
                teamUserVO.setCreateUser(userById);
                teamUserVO.setMemberId(memberId);
                teamUserVO.setIsJoin(memberId.contains(userId));
                resTeamUserVO.add(teamUserVO);
            }
        }
        return resTeamUserVO;
    }

    /**
     * 解散队伍
     * @param teamDisbandRequest
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean disbandTeam(TeamDisbandRequest teamDisbandRequest) {
        if (teamDisbandRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        //校验队伍是否存在
        Long teamId = teamDisbandRequest.getTeamId();
        Team team = getTeamById(teamId);

        //当前用户
        String userAccount = teamDisbandRequest.getUserAccount();
        String uuid = teamDisbandRequest.getUuid();
        User loginUser = userService.getLoginUser(userAccount, uuid);

        if (!team.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH, "禁止访问");
        }

        //移除所有加入队伍的关系
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(UserTeam::getTeamId, teamId);
        boolean remove = userTeamService.remove(queryWrapper);
        if (!remove) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除队伍失败");
        }

        return this.removeById(teamId);
    }

    /**
     * 根据 id 获取队伍信息
     *
     * @param teamId
     * @return
     */
    private Team getTeamById(Long teamId) {
        if (teamId == null || teamId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Team team = this.getById(teamId);
        if (team == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "队伍不存在");
        }
        return team;
    }

    /**
     * 获取某队伍当前人数
     *
     * @param teamId
     * @return
     */
    private List<UserTeam> hasJoinTeamUser(long teamId) {
        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        userTeamQueryWrapper.lambda().eq(UserTeam::getTeamId, teamId);
        return userTeamService.list(userTeamQueryWrapper);
    }
}




