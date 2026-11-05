-- =========================================================================
-- GateFlow - Distributed Token Bucket Rate Limiter
-- Atomic Lua Script executed in Redis
-- =========================================================================
-- KEYS[1] : Rate limit key (e.g. "gateflow:rl:tb:127.0.0.1:/api/limited")
-- ARGV[1] : Capacity (maximum tokens in bucket)
-- ARGV[2] : Refill rate (tokens added per second)
-- ARGV[3] : Current timestamp in milliseconds
-- ARGV[4] : Requested tokens (usually 1)
--
-- Return: { allowed (1 or 0), remaining_tokens, retry_after_ms }
-- =========================================================================

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

-- Validate inputs
if not capacity or capacity <= 0 then capacity = 100 end
if not refill_rate or refill_rate <= 0 then refill_rate = 10 end
if not now or now <= 0 then now = 0 end
if not requested or requested <= 0 then requested = 1 end

local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
local current_tokens
local last_refill

if not bucket[1] or not bucket[2] then
    -- Fresh bucket: initialize full capacity
    current_tokens = capacity
    last_refill = now
else
    current_tokens = tonumber(bucket[1])
    last_refill = tonumber(bucket[2])

    -- Calculate replenished tokens based on elapsed wall-clock time
    local elapsed_ms = math.max(0, now - last_refill)
    local tokens_to_add = (elapsed_ms / 1000.0) * refill_rate
    current_tokens = math.min(capacity, current_tokens + tokens_to_add)
    last_refill = now
end

-- Calculate TTL: double the time required to refill from empty to full capacity (minimum 60s)
local fill_time_sec = math.ceil(capacity / refill_rate)
local ttl = math.max(60, fill_time_sec * 2)

if current_tokens >= requested then
    -- Request Allowed
    local remaining = current_tokens - requested
    redis.call('HMSET', key, 'tokens', tostring(remaining), 'last_refill', tostring(last_refill))
    redis.call('EXPIRE', key, ttl)
    return {1, math.floor(remaining), 0}
else
    -- Request Rejected
    local missing = requested - current_tokens
    local retry_after_ms = math.ceil((missing / refill_rate) * 1000)
    if retry_after_ms <= 0 then retry_after_ms = 1 end

    redis.call('HMSET', key, 'tokens', tostring(current_tokens), 'last_refill', tostring(last_refill))
    redis.call('EXPIRE', key, ttl)
    return {0, math.floor(current_tokens), retry_after_ms}
end

