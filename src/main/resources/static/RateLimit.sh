iptables --flush
iptables --new-chain RATE-LIMIT
iptables --append INPUT  --jump RATE-LIMIT
iptables --append RATE-LIMIT \
    --match hashlimit \
    --hashlimit-mode srcip \
    --hashlimit-upto {UPTO_PLACEHOLDER} \
    --hashlimit-burst {BURST_PLACEHOLDER} \
    --hashlimit-name rate_limit_{TAG} \
    --jump ACCEPT
iptables --append RATE-LIMIT --jump DROP