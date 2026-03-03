-- Token Bucket Rate Limiter
-- KEYS[1]: rate limit key
-- ARGV[1]: max tokens (capacity)
-- ARGV[2]: refill rate (tokens per second)
-- ARGV[3]: requested tokens
-- ARGV[4]: current timestamp (seconds)

local key = KEYS[1]
local max_tokens = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])
local now = tonumber(ARGV[4])

local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens = tonumber(bucket[1])
local last_refill = tonumber(bucket[2])

if tokens == nil then
    tokens = max_tokens
    last_refill = now
end

local elapsed = now - last_refill
local refilled = elapsed * refill_rate
tokens = math.min(max_tokens, tokens + refilled)

local allowed = 0
if tokens >= requested then
    tokens = tokens - requested
    allowed = 1
end

redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
redis.call('EXPIRE', key, 3600)

return allowed
