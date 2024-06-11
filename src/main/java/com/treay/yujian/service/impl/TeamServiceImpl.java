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

import static com.treay.yujian.constant.RedisConstant.JOIN_TEAM_KEY;

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
        // 2. 是否登录，未登录不允许创建
        //通过userService来判断用户是否登录，如果未登录则抛出未登录的异常。
        User loginUser = userService.getLoginUser(addTeamRequest.getUserAccount(), addTeamRequest.getUuid());
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        long userId = loginUser.getId();
        // 3. 校验信息是否合法
        //对请求参数进行校验，包括队伍人数、队伍标题、队伍描述、队伍状态和密码等。如果不符合要求，则抛出相应的参数错误的异常
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
        // 7. 校验通过后，创建一个Team对象，将请求参数复制给这个对象。校验用户最多创建 5 个队伍
        //使用QueryWrapper查询已有的队伍数量，判断用户创建的队伍数量是否超过5个。如果超过，则抛出参数错误的异常。
        Team team = new Team();
        BeanUtils.copyProperties(addTeamRequest, team);

        QueryWrapper<Team> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(Team::getUserId, userId);// 设置  查询创建者id为自己的记录
        long hasTeamNum = this.count(queryWrapper); // 去数据库中查询
        if (hasTeamNum >= 5) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户最多创建 5 个队伍");
        }

        // 8. 插入队伍信息到队伍表
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
    public Boolean joinTeam(User loginUser, Long teamId,String password) {

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
        // 分布式锁保证幂等
        RLock lock = redissonClient.getLock(JOIN_TEAM_KEY);
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
    @Transactional(rollbackFor = Exception.class) // 声明该方法在执行时会开启事务，并且遇到异常时会回滚事务
    public Boolean quitTeam( User loginUser ,Long teamId) {
        //  根据 userid 和 teamid 在关系表中删除 关系
        //  如果 人数 = 1 那就在 team表中 根据 teamid 删除 team
        //  如果 人数 > 1 且 当前用户为 队长 那么 退出队伍后 在team表中 把userId 改为 加入队伍第二早的用户
        //  加入第二早的用户 需要在关系表中 根据teamid（查出的当前队伍·的所有用户关系） 再根据时间排序

        //查询是否加入队伍
        // 创建了一个用于构建查询条件的QueryWrapper对象。
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
        // 获取用户ID
        Long userId = loginUser.getId();
        // 通过lambda方法链式调用，设置查询条件，要求查询符合给定队伍ID和当前用户ID的记录。
        queryWrapper.lambda().eq(UserTeam::getTeamId, teamId).eq(UserTeam::getUserId, userId);
        // sql: select * from user_team where team_id = ? and user_id = ?
        long count = userTeamService.count(queryWrapper);
        // 如果记录数量为0，即当前用户未加入该队伍，则抛出业务异常，提示用户未加入队伍。
        if (count == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "未加入队伍");
        }
        // getTeamById 方法获取队伍信息
        Team team = getTeamById(teamId);
        // 查询队伍中的成员列表
        //  调用hasJoinTeamUser方法获取当前队伍中的成员列表。
        List<UserTeam> userTeams = this.hasJoinTeamUser(teamId);
        // 如果队伍中只剩下一人，删除队伍关系，并删除队伍
        if (userTeams.size() == 1) {
            // 删除队伍
            this.removeById(teamId);
        } else {
            // 如果队伍中有多人
            // 如果退出的是队长，则将队伍创建人转移给第二个加入的成员
            if (Objects.equals(team.getUserId(), userId)) {
                Team tempTeam = new Team();
                // 设置队伍ID
                tempTeam.setId(teamId);
                // 按加入时间排序
                userTeams.sort(Comparator.comparing(UserTeam::getJoinTime));
                // 将队伍创建人设置为第二个加入的成员
                tempTeam.setUserId(userTeams.get(1).getUserId());
                // 更新队伍信息，将队长转移给第二个加入的成员
                boolean result = this.updateById(tempTeam);

                if (!result) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新队长失败");
                }
            }
            // 如果退出的不是队长，则直接退出队伍，删除用户-队伍关系
        }
        // 删除用户-队伍关系
        return userTeamService.remove(queryWrapper);
    }
    @Override
    public List<TeamUserVO> queryTeams(TeamQueryRequest teamQueryRequest) {
        //1. 从请求参数中取出队伍名称等查询条件，如果存在则作为查询条件
        //当前登录用户
        User loginUser = userService.getLoginUser(teamQueryRequest.getUserAccount(), teamQueryRequest.getUuid());
        // 构建查询包装器
        QueryWrapper<Team> queryWrapper = new QueryWrapper<>();

        // 动态SQL构建，根据不同的查询条件进行过滤
        queryWrapper.lambda()
                //如果 teamQueryRequest.getId() 不为 null 并且大于 0，则添加一个等于条件，查询 Team 表中的 id 字段等于 teamQueryRequest.getId()。
                .eq(teamQueryRequest.getId() != null && teamQueryRequest.getId() > 0, Team::getId, teamQueryRequest.getId()) // 根据ID查询
                //如果 teamQueryRequest.getIdList() 不为空，则添加一个 in 条件，查询 Team 表中的 id 字段是否在 teamQueryRequest.getIdList() 列表中。
                //teamQueryRequest.getIdList() IS NOT NULL AND id IN (teamQueryRequest.getIdList())
                .in(!CollectionUtils.isEmpty(teamQueryRequest.getIdList()), Team::getId, teamQueryRequest.getIdList())// 根据teamId列表查询（我加入的）
                //如果 teamQueryRequest.getUserId() 不为 null 并且大于 0，则添加一个等于条件，查询 Team 表中的 userId 字段等于 teamQueryRequest.getUserId()。
                //teamQueryRequest.getUserId() IS NOT NULL AND teamQueryRequest.getUserId() > 0 AND userId = teamQueryRequest.getUserId()
                .eq(teamQueryRequest.getUserId() != null && teamQueryRequest.getUserId() > 0, Team::getUserId, teamQueryRequest.getUserId());// 根据创建者ID查询
        // 如果存在搜索文本，对名称和描述进行模糊查询
        if (StringUtils.isNotBlank(teamQueryRequest.getSearchText())) {
            queryWrapper.lambda()
                    //分别查询 Team 表中的 name 和 description 字段是否包含 teamQueryRequest.getSearchText()
                    //teamQueryRequest.getSearchText() IS NOT NULL AND (name LIKE '%teamQueryRequest.getSearchText%' OR description LIKE '%teamQueryRequest.getSearchText%')
                    .like(Team::getName, teamQueryRequest.getSearchText())
                    .or()
                    .like(Team::getDescription, teamQueryRequest.getSearchText());
        }
        // 过滤未过期或永久有效的队伍
        //WHERE expireTime > CURRENT_TIMESTAMP OR expireTime IS NULL; 查询Team表中expireTime字段大于当前时间或者为空的记录
        queryWrapper.lambda().and(qw -> qw.gt(Team::getExpireTime, new Date()).or().isNull(Team::getExpireTime));

        // 如果状态有效且为公开或秘密，进行状态过滤
        // 从 teamQueryRequest 对象中获取状态字段的值
        Integer status = teamQueryRequest.getStatus();
        // 使用 TeamStatusEnum 枚举类中的静态方法 getEnumByValue 将整数类型的 status 转换成对应的枚举类型
        TeamStatusEnum statusEnum = TeamStatusEnum.getEnumByValue(status);
        // 检查 statusEnum 是否为非空，并且它的值是 PUBLIC 或者 SECRET
        if (statusEnum != null && (statusEnum.equals(TeamStatusEnum.PUBLIC) || statusEnum.equals(TeamStatusEnum.SECRET))) {
            // 如果状态是 PUBLIC 或 SECRET，那么在 queryWrapper 中添加一个查询条件
            queryWrapper.lambda().eq(Team::getStatus, status);
        }
        // 执行查询获取队伍列表
        List<Team> teamList= this.list(queryWrapper);
        // 如果没有查询到任何队伍，直接返回空列表
        if (CollectionUtils.isEmpty(teamList)) {
            return new ArrayList<>();
        }
        // 初始化一个List<TeamUserVO>类型的列表respTeamUserVO，用于存储最终要返回给前端的队伍信息
        List<TeamUserVO> respTeamUserVO = new ArrayList<>();
        // 关联查询用户信息并脱敏遍历队伍，构建响应数据  根据每个队伍id查所有加入这个队伍的用户id，再根据用户id查
        for (Team team : teamList) {
            // 过滤已过期的队伍
            if (team.getExpireTime() == null || team.getExpireTime().after(new Date())) {
                // 构建VO对象并复制基础信息
                TeamUserVO teamUserVO = new TeamUserVO();
                BeanUtils.copyProperties(team, teamUserVO);
                // 获取队伍成员信息并脱敏
                ArrayList<User> userList = new ArrayList<>();
                ArrayList<Long> memberId = new ArrayList<>();
                Long teamId = team.getId();
                QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
                userTeamQueryWrapper.lambda().eq(UserTeam::getTeamId, teamId);//查询条件
                //调用userTeamService.list(userTeamQueryWrapper)方法执行查询，获取与当前团队相关的所有UserTeam记录列表。
                List<UserTeam> list = userTeamService.list(userTeamQueryWrapper);
                for (UserTeam userTeam : list) {
                    //使用userService.getById(userTeam.getUserId())方法根据UserTeam对象中的userId获取完整的用户信息。
                    User user = userService.getById(userTeam.getUserId());
                    User safetyUser = userService.getSafetyUser(user); // 脱敏处理
                    userList.add(safetyUser);

                    //所有加入队伍的成员id
                    memberId.add(user.getId());
                    teamUserVO.setUserList(userList);
                }
                // 设置创建者信息
                User userById = userService.getById(team.getUserId());
                teamUserVO.setCreateUsername(userById.getUsername());
                teamUserVO.setCreateAvatarUrl(userById.getAvatarUrl());
                teamUserVO.setCreateUser(userById);
                // 设置成员ID列表
                teamUserVO.setMemberId(memberId);
                // 判断当前用户是否已加入队伍
                Long userId = loginUser.getId(); //获取当前登录用户的ID
                teamUserVO.setIsJoin(memberId.contains(userId));
                // 添加到响应列表
                respTeamUserVO.add(teamUserVO);
            }
        }
        // 返回最终的响应数据
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
     * @param loginUser
     * @param teamId
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean disbandTeam(User loginUser,Long teamId) {
        // 根据队伍ID获取队伍信息
        Team team = getTeamById(teamId);
        // 验证当前用户是否是队长，若不是则抛出权限异常
        // 检查当前用户是否是队伍的队长
        if (!team.getUserId().equals(loginUser.getId())) {
            // 抛出无权限的业务异常
            throw new BusinessException(ErrorCode.NO_AUTH, "禁止访问");
        }

        //移除所有加入队伍的关系
        // 创建查询条件
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
        // 设置查询条件，找到所有加入该队伍的关系
        queryWrapper.lambda().eq(UserTeam::getTeamId, teamId);
        // 调用service移除符合条件的关系
        boolean remove = userTeamService.remove(queryWrapper);
        // 检查关系是否成功移除
        if (!remove) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除队伍失败");
        }

        // 删除队伍信息并返回删除结果
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




