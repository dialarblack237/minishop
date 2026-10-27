local capacity = tonumber(ARGV[1])
local rate     = tonumber(ARGV[2])
local now      = tonumber(ARGV[3])

local bucket = redis.call('HMGET', KEYS[1], 'tokens', 'refilledAt')
local tokens    = tonumber(bucket[1]) or capacity
local refilled  = tonumber(bucket[2]) or now

tokens = math.min(capacity, tokens + (now - refilled) * rate)

local allowed, retryAfter
if tokens >= 1 then
    tokens = tokens - 1
    allowed = 1
else
    allowed = 0
    retryAfter = math.ceil((1 - tokens) / rate)
end

redis.call('HMSET', KEYS[1], 'tokens', tokens, 'refilledAt', now)
redis.call('EXPIRE', KEYS[1], 3600)

return { allowed, retryAfter or 0 }