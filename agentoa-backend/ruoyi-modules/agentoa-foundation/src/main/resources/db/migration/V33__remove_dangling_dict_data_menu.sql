-- Remove the dangling hidden menu left over from the upstream baseline.
-- Menu 132 points at component 'system/dict/data', which has no matching view in
-- agentoa-frontend (dictionary data is managed inline by 'system/dict/index').
-- The route never resolves (loadView returns undefined) and nothing links to its
-- path 'dict-data/index/:dictId', so the record is dropped instead of disabled.
DELETE FROM sys_role_menu WHERE menu_id = 132;
DELETE FROM sys_menu WHERE menu_id = 132 AND component = 'system/dict/data';
