package com.treay.yujian.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.treay.yujian.model.request.*;
import com.treay.yujian.model.domain.Team;
import com.treay.yujian.model.domain.User;
import com.treay.yujian.model.dto.TeamQuery;
import com.treay.yujian.model.vo.TeamUserVO;

import java.util.List;

/**
 * 队伍服务
 *
 * @author Treay
 * 
 */
public interface TeamService extends IService<Team> {

    /**
     * 创建队伍
     *
     * @param addTeamRequest
     * @return
     */
    long addTeam(AddTeamRequest addTeamRequest);



    /**
     * 更新队伍
     *
     * @param teamUpdateRequest
     * @param loginUser
     * @return
     */
    boolean updateTeam(TeamUpdateRequest teamUpdateRequest, User loginUser);

    /**
     * 加入队伍
     * @param loginUser
     * @param teamId
     * @return
     */
    Boolean joinTeam(User loginUser, Long teamId,String password);

    /**
     * 退出队伍
     *
     * @param teamQuitRequest
     * @return
     */
    Boolean quitTeam(TeamQuitRequest teamQuitRequest);


    /**
     * 搜索队伍
     * @param teamQueryRequest
     * @return
     */
    List<TeamUserVO> queryTeams(TeamQueryRequest teamQueryRequest);

    /**
     * 解散队伍
     * @param teamDisbandRequest
     * @return
     */
    Boolean disbandTeam(TeamDisbandRequest teamDisbandRequest);


    /**
     * 获取我创建的队伍
     * @param teamQueryRequest
     * @return
     */
    List<TeamUserVO> listMyCreateTeams(TeamQueryRequest teamQueryRequest);

    /**
     * 获取我加入的队伍
     * @param teamQueryRequest
     * @return
     */
    List<TeamUserVO> listMyJoinTeams(TeamQueryRequest teamQueryRequest);

    /**
     * 根据关键词搜索队伍
     * @param teamQueryRequest
     * @return
     */
    List<TeamUserVO> searchTeams(TeamQueryRequest teamQueryRequest);
}
