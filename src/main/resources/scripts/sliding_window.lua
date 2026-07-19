local key          = KEYS[1]
local max_requests = tonumber(ARGV[1])
local window_ms    = tonumber(ARGV[2])
local now          = tonumber(ARGV[3])

local window_start = now - window_ms

redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)

local current_count = redis.call('ZCARD', key)

local allowed = 0
local retry_after_ms = 0

if current_count < max_requests then
	local member = now .. '-' .. math.random(1, 1000000)
	redis.call('ZADD', key, now, member)
	allowed = 1
	current_count = current_count + 1
else
	local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
	local oldest_score = tonumber(oldest[2])
	retry_after_ms = (oldest_score + window_ms) - now
end

redis.call('PEXPIRE', key, window_ms)

local remaining = max_requests - current_count
if remaining < 0 then remaining = 0 end

return { allowed, remaining, retry_after_ms }