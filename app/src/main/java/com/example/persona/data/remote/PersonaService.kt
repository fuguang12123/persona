package com.example.persona.data.remote

import com.example.persona.data.model.Persona
import com.example.persona.data.model.PersonaRecommendationDto
import androidx.annotation.NonNull
import androidx.annotation.IntRange
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

// 基础响应包装
data class Result<T>(val code: Int, val message: String, val data: T?) {
    fun isSuccess() = code == 200
}

data class GenerateRequest(val name: String)

/**
 * @class com.example.persona.data.remote.PersonaService
 * @description 面向 Persona 领域的 Retrofit 契约接口，定义了广场、推荐、基础 CRUD 以及关注相关的网络交互协议。该接口承载“Persona 创作与社交”主干数据通道：UI 通过 ViewModel 调用 Repository，Repository 基于 Hilt 注入的 Retrofit 实例对外发起请求，并以 Room 作为本地 SSOT 缓存，用以支撑信息流与详情页的稳定显示。接口参数在注释中声明约束与可选性，便于文档生成与静态校验；错误码与返回模型以统一包装类 `Result<T>` 表达，以减少上层分支复杂度、提升可观测性。该契约与《最终作业.md》中的基础与进阶需求直接对应，覆盖 Persona 创作（B1）、社交广场（B2/B3）、直接对话入口（B4，详情页跳转）、推荐系统（C5）等路径，并作为混合架构中云端侧的外部依赖点。
 * @author Persona Team <persona@project.local>
 * @version v1.0.0
 * @since 2025-11-30
 * @see com.example.persona.data.repository.PersonaRepository
 * @关联功能 REQ-B1 Persona创作；REQ-B2/B3 社交广场；REQ-B4 直接对话；REQ-C5 智能推荐
 */
interface PersonaService {
    // --- 广场与推荐 ---

    // [修改] 增加 page 和 size 参数支持分页
    /**
     * 功能: 拉取 Persona 广场信息流，支持分页，承载“浏览与互动”入口的数据源；上层将结果持久化到 Room 以提升滚动体验与离线容错。
     * 实现逻辑:
     * 1. 通过 GET `personas/feed` 请求后端分页数据
     * 2. 上层根据 `Result` 成功与否写入本地缓存并驱动 UI 刷新
     * 3. 边界处理: 后端返回空列表或错误码时，页面维持现有缓存
     * @param page Int - 页码 (约束: @NonNull, @IntRange(from=1))
     * @param size Int - 每页大小 (约束: @NonNull, @IntRange(from=1,to=100))
     * @return Result<List<Persona>> - 标准返回包装，code==200 表示成功；data 为空表示暂无数据
     * @throws Exception 网络异常、协议不一致等由上层统一捕获并提示
     * 关联功能: REQ-B3 社交广场-浏览与互动
     * 复杂度分析: 时间 O(N) | 空间 O(N)
     * 线程安全: 是 - Retrofit Call 在 IO 调度执行，由上层协程隔离
     */
    @GET("personas/feed")
    suspend fun getFeed(
        @Query("page") page: Int,
        @Query("size") size: Int
    ): Result<List<Persona>>

    // 推荐接口通常不分页，或者有单独的分页逻辑
    /**
     * 功能: 获取“发现页”的推荐 Persona 列表；支持可选用户 ID 以便后端做个性化排序。
     * 实现逻辑:
     * 1. 携带 `X-User-Id` 头（可空）请求推荐列表
     * 2. 上层转换为 Domain 模型并展示评分/原因
     * 3. 边界处理: `userId` 缺失时走默认推荐集
     * @param userId Long? - 用户ID (约束: Nullable，缺省为匿名推荐)
     * @return Result<List<PersonaRecommendationDto>> - 推荐项集合，含 matchScore 与 reason
     * @throws Exception 网络错误由上层统一处理
     * 关联功能: REQ-C5 智能推荐系统
     * 复杂度分析: 时间 O(N) | 空间 O(N)
     * 线程安全: 是 - IO 线程执行
     */
    @GET("personas/recommend")
    suspend fun getRecommend(
        @Header("X-User-Id") userId: Long?
    ): Result<List<PersonaRecommendationDto>>

    // --- 基础操作 ---

    /**
     * 功能: 获取 Persona 详情，用于聊天入口及信息展示；成功后通常写入本地数据库以供 UI 复用。
     * 实现逻辑: 直接 GET `personas/{id}`，由上层做持久化与 UI 映射。
     * @param id Long - Persona ID (@NonNull)
     * @return Result<Persona> - 详情模型
     * 关联功能: REQ-B4 直接对话入口、资料页
     */
    @GET("personas/{id}")
    suspend fun getPersona(@Path("id") id: Long): Result<Persona>

    /**
     * 功能: 创建 Persona（名称/头像/设定），对应“Persona 创作”的核心路径；成功后建议刷新广场第一页以展示新项。
     * 实现逻辑: POST `personas`，上层在成功后拉取 feed 更新 Room。
     * @param persona Persona - 创建请求体 (@NonNull)
     * @return Result<String> - 成功消息或ID字符串（后端定义）
     * 关联功能: REQ-B1 Persona创作
     */
    @POST("personas")
    suspend fun createPersona(@Body persona: Persona): Result<String>

    /**
     * 功能: 更新 Persona 资料；上层在成功后同步本地缓存。
     * @param id Long - Persona ID (@NonNull)
     * @param persona Persona - 更新内容 (@NonNull)
     * @return Result<String>
     * 关联功能: REQ-B1 Persona创作（资料维护）
     */
    @PUT("personas/{id}")
    suspend fun updatePersona(@Path("id") id: Long, @Body persona: Persona): Result<String>

    /**
     * 功能: AI 辅助生成 Persona 设定描述；用于创作流程中的自动化建议。
     * @param req GenerateRequest - 输入名称 (@NonNull)
     * @return Result<String> - 生成的描述文本
     * 关联功能: REQ-B1 Persona创作-AI辅助生成
     */
    @POST("ai/generate-persona")
    suspend fun generatePersonaProfile(@Body req: GenerateRequest): Result<String>

    // --- 关注相关接口 ---

    /**
     * 功能: 关注/取消关注指定 Persona，用于社交广场互动。
     * @param id Long - Persona ID (@NonNull)
     * @return Result<Boolean> - true 表示关注状态变更成功
     * 关联功能: REQ-B3 社交广场-关注互动
     */
    @POST("follows/{id}")
    suspend fun toggleFollow(@Path("id") id: Long): Result<Boolean>

    /**
     * 功能: 查询关注状态。
     * @param id Long - Persona ID (@NonNull)
     * @return Result<Boolean> - true 表示已关注
     * 关联功能: REQ-B3 社交广场-关注状态显示
     */
    @GET("follows/status/{id}")
    suspend fun getFollowStatus(@Path("id") id: Long): Result<Boolean>

    /**
     * 功能: 获取当前用户已关注的 Persona 列表。
     * @return Result<List<Persona>>
     * 关联功能: REQ-B3 社交广场-关注列表
     */
    @GET("follows/list")
    suspend fun getFollowedList(): Result<List<Persona>>
}
