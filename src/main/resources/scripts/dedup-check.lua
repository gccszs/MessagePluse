-- Redis Lua script for atomic deduplication check and add
-- KEYS[1]: Redis key for the message set
-- ARGV[1]: messageId to check and add
-- ARGV[2]: TTL in seconds

local key = KEYS[1]
local messageId = ARGV[1]
local ttl = tonumber(ARGV[2])

-- Check if messageId exists
local exists = redis.call('SISMEMBER', key, messageId)

if exists == 1 then
    return 1  -- Duplicate found
else
    -- Add messageId to set
    redis.call('SADD', key, messageId)
    -- Set expiration
    redis.call('EXPIRE', key, ttl)
    return 0  -- Not duplicate
end
