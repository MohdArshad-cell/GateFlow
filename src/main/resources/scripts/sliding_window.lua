-- =========================================================================
-- GateFlow - Distributed Sliding Window Log Rate Limiter
-- Atomic Lua Script executed in Redis using Sorted Sets (ZSET)
-- =========================================================================
-- KEYS[1] : Rate limit key (e.g. "gateflow:rl:sw:127.0.0.1:/api/login")
-- ARGV[1] : Window size in milliseconds
-- ARGV[2] : Max requests in window
-- ARGV[3] : Current timestamp in milliseconds
-- ARGV[4] : Unique request identifier (e.g. UUID)
--
-- Return: { allowed (1 or 0), remaining_requests, retry_after_ms }
-- =========================================================================

local key = KEYS[1]
local window_ms = tonumber(ARGV[1])
local max_requests = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local request_id = tostring(ARGV[4])

-- Validate inputs
if not window_ms or window_ms <= 0 then window_ms = 60000 end
if not max_requests or max_requests <= 0 then max_requests = 100 end
if not now or now <= 0 then now = 0 end
if not request_id or request_id == '' then request_id = tostring(now) end

-- Step 1: Evict stale records older than current sliding window
local window_start = now - window_ms
redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)

-- Step 2: Query active request count in this sliding window
local current_count = redis.call('ZCARD', key)

-- TTL: ensure key auto-expires after twice the window duration (minimum 60s)
local ttl_seconds = math.max(60, math.ceil((window_ms / 1000.0) * 2))

if current_count < max_requests then
    -- Request Allowed: record timestamp in sorted set
    redis.call('ZADD', key, now, request_id)
    redis.call('EXPIRE', key, ttl_seconds)

    local remaining = max_requests - current_count - 1
    return {1, remaining, 0}
else
    -- Request Rejected: calculate precise retry_after_ms from oldest entry in active window
    local oldest_record = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    local retry_after_ms = window_ms

    if oldest_record and #oldest_record >= 2 then
        local oldest_time = tonumber(oldest_record[2])
        if oldest_time then
            retry_after_ms = math.max(1, (oldest_time + window_ms) - now)
        end
    end

    redis.call('EXPIRE', key, ttl_seconds)
    return {0, 0, retry_after_ms}
end

