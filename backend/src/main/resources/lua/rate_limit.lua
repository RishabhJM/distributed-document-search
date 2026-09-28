-- rate_limit.lua
-- Atomic Token Bucket Rate Limiter
-- KEYS[1] = bucket key (e.g., ratelimit:v1:acme)
-- ARGV[1] = bucket capacity (max tokens)
-- ARGV[2] = refill rate per second
-- ARGV[3] = requested tokens (typically 1)
-- ARGV[4] = current epoch timestamp in milliseconds

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])
local now = tonumber(ARGV[4])

local data = redis.call('HMGET', key, 'tokens', 'last_refreshed')
local tokens = tonumber(data[1])
local last_refreshed = tonumber(data[2])

if tokens == nil or last_refreshed == nil then
    tokens = capacity
    last_refreshed = now
else
    local delta = math.max(0, now - last_refreshed)
    local tokens_to_add = (delta / 1000.0) * refill_rate
    tokens = math.min(capacity, tokens + tokens_to_add)
    last_refreshed = now
end

local allowed = 0
if tokens >= requested then
    tokens = tokens - requested
    allowed = 1
end

redis.call('HMSET', key, 'tokens', tokens, 'last_refreshed', last_refreshed)
-- Expire after 120 seconds of inactivity
redis.call('EXPIRE', key, 120)

local missing_tokens = math.max(0, requested - tokens)
local wait_seconds = math.ceil(missing_tokens / refill_rate)

return { allowed, math.floor(tokens), wait_seconds }
